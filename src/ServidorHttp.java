import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import util.ConexaoDB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camada HTTP do Sistema de Gestão de Resíduos Hospitalares (arquivo único).
 *
 * - Usa apenas o JDK (com.sun.net.httpserver) + o driver MySQL já presente no projeto.
 * - Reutiliza util.ConexaoDB para abrir conexões (ou as variáveis DB_URL, DB_USER, DB_PASS, se definidas).
 * - Usa as colunas do DDL: Residuo.nome_residuo, classificacao_risco, peso_kg, id_setor, id_coleta etc.
 * - Entrega JSON em UTF-8 e serve o frontend estático da pasta "frontend" (ou FRONTEND_DIR).
 *
 * Como rodar: compile junto com o projeto e execute a classe ServidorHttp (porta 8080, ou PORT).
 *
 * ENDPOINTS
 *   GET    /api/saude
 *   GET    /api/dashboard
 *   GET|POST            /api/setores           GET|PUT|DELETE  /api/setores/{id}
 *   GET|POST            /api/descartes         GET|PUT|DELETE  /api/descartes/{id}
 *   GET|POST            /api/coletas           GET|PUT|DELETE  /api/coletas/{id}
 *   GET|POST            /api/residuos          GET|PUT|DELETE  /api/residuos/{id}    (GET aceita ?grupo=A&q=texto)
 *   GET                 /api/funcionarios      (derivado de Coleta.funcionario_responsavel; não há tabela)
 *   POST   /api/saidas  (cria Coleta + Residuo na mesma transação e valida grupo x método de destino)
 */
public class ServidorHttp {

    // ============================================================ Configuração
    private static final int PORTA = Integer.parseInt(env("PORT", "8080"));
    private static final Path PASTA_FRONT = Paths.get(env("FRONTEND_DIR", "frontend")).toAbsolutePath().normalize();
    private static final int MAX_BODY = 1_000_000;
    private static final Pattern GRUPO = Pattern.compile("^Grupo [A-E]( - .*)?$");

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORTA), 0);
        server.createContext("/", ServidorHttp::atender);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Gestão de Resíduos Hospitalares: http://localhost:" + PORTA);
        System.out.println("Frontend estático em: " + PASTA_FRONT);
    }

    // ============================================================ Erros de negócio
    static class ApiErro extends RuntimeException {
        final int status;
        ApiErro(int status, String msg) { super(msg); this.status = status; }
    }

    private static ApiErro invalido(String msg) { return new ApiErro(400, msg); }
    private static ApiErro naoEncontrado(String msg) { return new ApiErro(404, msg); }

    // ============================================================ Roteador
    private static void atender(HttpExchange ex) throws IOException {
        long t0 = System.currentTimeMillis();
        int status = 500;
        try {
            cors(ex);
            String metodo = ex.getRequestMethod().toUpperCase();
            String path = ex.getRequestURI().getPath();
            if (metodo.equals("OPTIONS")) { status = 204; ex.sendResponseHeaders(204, -1); return; }

            if (path.equals("/api") || path.startsWith("/api/")) {
                Object corpo;
                try {
                    corpo = rotaApi(ex, metodo, path);
                    status = (corpo instanceof Resposta) ? ((Resposta) corpo).status : 200;
                    if (corpo instanceof Resposta) corpo = ((Resposta) corpo).corpo;
                } catch (ApiErro e) {
                    status = e.status; corpo = mapa("erro", e.getMessage());
                } catch (SQLIntegrityConstraintViolationException e) {
                    status = 409; corpo = mapa("erro", "Operação bloqueada por vínculos existentes (chave estrangeira): " + e.getMessage());
                } catch (SQLException e) {
                    status = e.getSQLState() != null && e.getSQLState().startsWith("23") ? 409 : 500;
                    corpo = mapa("erro", "Erro de banco de dados: " + e.getMessage());
                } catch (RuntimeException e) {
                    status = 503; corpo = mapa("erro", "Falha ao acessar o banco: " + e.getMessage());
                }
                enviarJson(ex, status, corpo);
            } else if (metodo.equals("GET") || metodo.equals("HEAD")) {
                status = servirEstatico(ex, path);
            } else {
                status = 405; enviarJson(ex, 405, mapa("erro", "Método não permitido"));
            }
        } catch (Throwable t) {
            t.printStackTrace();
            try { enviarJson(ex, 500, mapa("erro", "Erro interno do servidor")); } catch (Throwable ignored) { }
        } finally {
            System.out.println(ex.getRequestMethod() + " " + ex.getRequestURI() + " -> " + status + " (" + (System.currentTimeMillis() - t0) + " ms)");
            ex.close();
        }
    }

    static class Resposta {
        final int status; final Object corpo;
        Resposta(int s, Object c) { status = s; corpo = c; }
    }

    private static Object rotaApi(HttpExchange ex, String metodo, String path) throws Exception {
        String[] p = path.replaceAll("^/+|/+$", "").split("/");   // api, recurso, id
        String recurso = p.length > 1 ? p[1] : "";
        Integer id = null;
        if (p.length > 2) {
            try { id = Integer.parseInt(p[2]); } catch (NumberFormatException e) { throw invalido("Identificador inválido: " + p[2]); }
        }
        if (p.length > 3) throw naoEncontrado("Rota não encontrada: " + path);

        switch (recurso) {
            case "saude":
                if (!metodo.equals("GET")) throw metodoErro();
                return mapa("status", "ok", "hora", LocalDateTime.now().toString());
            case "dashboard":
                if (!metodo.equals("GET")) throw metodoErro();
                return dashboard();
            case "setores":      return crudSetor(ex, metodo, id);
            case "descartes":    return crudDescarte(ex, metodo, id);
            case "coletas":      return crudColeta(ex, metodo, id);
            case "residuos":     return crudResiduo(ex, metodo, id);
            case "funcionarios":
                if (!metodo.equals("GET") || id != null) throw metodoErro();
                return funcionarios();
            case "saidas":
                if (!metodo.equals("POST") || id != null) throw metodoErro();
                return registrarSaida(corpoJson(ex));
            default:
                throw naoEncontrado("Rota não encontrada: " + path);
        }
    }

    private static ApiErro metodoErro() { return new ApiErro(405, "Método não permitido para esta rota"); }

    // ============================================================ Setor
    private static Object crudSetor(HttpExchange ex, String m, Integer id) throws Exception {
        try (Connection c = conn()) {
            switch (m) {
                case "GET":
                    if (id == null) return consultar(c, "SELECT s.id_setor, s.nome_setor, "
                            + "(SELECT COUNT(*) FROM Residuo r WHERE r.id_setor = s.id_setor) AS total_residuos, "
                            + "(SELECT COALESCE(SUM(r.peso_kg),0) FROM Residuo r WHERE r.id_setor = s.id_setor) AS peso_total_kg "
                            + "FROM Setor s ORDER BY s.id_setor");
                    return um(c, "SELECT id_setor, nome_setor FROM Setor WHERE id_setor = ?", "Setor", id);
                case "POST": {
                    Map<String, Object> b = corpoJson(ex);
                    String nome = texto(b, "nome_setor", 100, true);
                    int novo = inserir(c, "INSERT INTO Setor (nome_setor) VALUES (?)", nome);
                    return new Resposta(201, um(c, "SELECT id_setor, nome_setor FROM Setor WHERE id_setor = ?", "Setor", novo));
                }
                case "PUT": {
                    exigeId(id);
                    String nome = texto(corpoJson(ex), "nome_setor", 100, true);
                    exigeLinha(executar(c, "UPDATE Setor SET nome_setor = ? WHERE id_setor = ?", nome, id), "Setor");
                    return um(c, "SELECT id_setor, nome_setor FROM Setor WHERE id_setor = ?", "Setor", id);
                }
                case "DELETE":
                    exigeId(id);
                    exigeLinha(executar(c, "DELETE FROM Setor WHERE id_setor = ?", id), "Setor");
                    return mapa("removido", true, "id_setor", id);
                default: throw metodoErro();
            }
        }
    }

    // ============================================================ Descarte_Final
    private static Object crudDescarte(HttpExchange ex, String m, Integer id) throws Exception {
        final String um = "SELECT id_descarte, empresa_terceirizada, comprovante_conformidade FROM Descarte_Final WHERE id_descarte = ?";
        try (Connection c = conn()) {
            switch (m) {
                case "GET":
                    if (id == null) return consultar(c, "SELECT d.id_descarte, d.empresa_terceirizada, d.comprovante_conformidade, "
                            + "(SELECT COUNT(*) FROM Coleta c WHERE c.id_descarte = d.id_descarte) AS total_coletas "
                            + "FROM Descarte_Final d ORDER BY d.id_descarte");
                    return um(c, um, "Empresa de descarte", id);
                case "POST": {
                    Map<String, Object> b = corpoJson(ex);
                    int novo = inserir(c, "INSERT INTO Descarte_Final (empresa_terceirizada, comprovante_conformidade) VALUES (?, ?)",
                            texto(b, "empresa_terceirizada", 100, true), texto(b, "comprovante_conformidade", 100, false));
                    return new Resposta(201, um(c, um, "Empresa de descarte", novo));
                }
                case "PUT": {
                    exigeId(id);
                    Map<String, Object> b = corpoJson(ex);
                    exigeLinha(executar(c, "UPDATE Descarte_Final SET empresa_terceirizada = ?, comprovante_conformidade = ? WHERE id_descarte = ?",
                            texto(b, "empresa_terceirizada", 100, true), texto(b, "comprovante_conformidade", 100, false), id), "Empresa de descarte");
                    return um(c, um, "Empresa de descarte", id);
                }
                case "DELETE":
                    exigeId(id);
                    exigeLinha(executar(c, "DELETE FROM Descarte_Final WHERE id_descarte = ?", id), "Empresa de descarte");
                    return mapa("removido", true, "id_descarte", id);
                default: throw metodoErro();
            }
        }
    }

    // ============================================================ Coleta
    private static final String SQL_COLETA = "SELECT c.id_coleta, c.data_hora, c.funcionario_responsavel, c.id_descarte, "
            + "d.empresa_terceirizada, "
            + "(SELECT COUNT(*) FROM Residuo r WHERE r.id_coleta = c.id_coleta) AS total_residuos, "
            + "(SELECT COALESCE(SUM(r.peso_kg),0) FROM Residuo r WHERE r.id_coleta = c.id_coleta) AS peso_total_kg "
            + "FROM Coleta c LEFT JOIN Descarte_Final d ON d.id_descarte = c.id_descarte ";

    private static Object crudColeta(HttpExchange ex, String m, Integer id) throws Exception {
        try (Connection c = conn()) {
            switch (m) {
                case "GET":
                    if (id == null) return consultar(c, SQL_COLETA + "ORDER BY c.data_hora DESC, c.id_coleta DESC");
                    return um(c, SQL_COLETA + "WHERE c.id_coleta = ?", "Coleta", id);
                case "POST": {
                    Map<String, Object> b = corpoJson(ex);
                    Integer desc = inteiro(b, "id_descarte", false);
                    exigeDescarte(c, desc);
                    int novo = inserir(c, "INSERT INTO Coleta (data_hora, funcionario_responsavel, id_descarte) VALUES (?, ?, ?)",
                            dataHora(b), texto(b, "funcionario_responsavel", 100, true), desc);
                    return new Resposta(201, um(c, SQL_COLETA + "WHERE c.id_coleta = ?", "Coleta", novo));
                }
                case "PUT": {
                    exigeId(id);
                    Map<String, Object> b = corpoJson(ex);
                    Integer desc = inteiro(b, "id_descarte", false);
                    exigeDescarte(c, desc);
                    exigeLinha(executar(c, "UPDATE Coleta SET data_hora = ?, funcionario_responsavel = ?, id_descarte = ? WHERE id_coleta = ?",
                            dataHora(b), texto(b, "funcionario_responsavel", 100, true), desc, id), "Coleta");
                    return um(c, SQL_COLETA + "WHERE c.id_coleta = ?", "Coleta", id);
                }
                case "DELETE":
                    exigeId(id);
                    exigeLinha(executar(c, "DELETE FROM Coleta WHERE id_coleta = ?", id), "Coleta");
                    return mapa("removido", true, "id_coleta", id);
                default: throw metodoErro();
            }
        }
    }

    // ============================================================ Residuo
    private static final String SQL_RESIDUO = "SELECT r.id_residuo, r.nome_residuo, r.classificacao_risco, r.peso_kg, "
            + "r.id_setor, s.nome_setor, r.id_coleta, c.data_hora, c.funcionario_responsavel, c.id_descarte, d.empresa_terceirizada "
            + "FROM Residuo r JOIN Setor s ON s.id_setor = r.id_setor "
            + "LEFT JOIN Coleta c ON c.id_coleta = r.id_coleta "
            + "LEFT JOIN Descarte_Final d ON d.id_descarte = c.id_descarte ";

    private static Object crudResiduo(HttpExchange ex, String m, Integer id) throws Exception {
        try (Connection c = conn()) {
            switch (m) {
                case "GET": {
                    if (id != null) return um(c, SQL_RESIDUO + "WHERE r.id_residuo = ?", "Resíduo", id);
                    Map<String, String> q = query(ex);
                    StringBuilder sql = new StringBuilder(SQL_RESIDUO + "WHERE 1=1 ");
                    List<Object> ps = new ArrayList<>();
                    if (q.containsKey("grupo")) {
                        if (!q.get("grupo").matches("[A-Ea-e]")) throw invalido("Parâmetro grupo deve ser A, B, C, D ou E");
                        sql.append("AND r.classificacao_risco LIKE ? "); ps.add("Grupo " + q.get("grupo").toUpperCase() + "%");
                    }
                    if (q.containsKey("setor")) { sql.append("AND r.id_setor = ? "); ps.add(parseInt(q.get("setor"), "setor")); }
                    if (q.containsKey("q") && !q.get("q").isBlank()) {
                        sql.append("AND (r.nome_residuo LIKE ? OR s.nome_setor LIKE ?) ");
                        String like = "%" + q.get("q").trim() + "%"; ps.add(like); ps.add(like);
                    }
                    sql.append("ORDER BY r.id_residuo DESC");
                    return consultar(c, sql.toString(), ps.toArray());
                }
                case "POST": {
                    Object[] v = validarResiduo(c, corpoJson(ex));
                    int novo = inserir(c, "INSERT INTO Residuo (nome_residuo, classificacao_risco, peso_kg, id_setor, id_coleta) VALUES (?, ?, ?, ?, ?)", v);
                    return new Resposta(201, um(c, SQL_RESIDUO + "WHERE r.id_residuo = ?", "Resíduo", novo));
                }
                case "PUT": {
                    exigeId(id);
                    Object[] v = validarResiduo(c, corpoJson(ex));
                    exigeLinha(executar(c, "UPDATE Residuo SET nome_residuo = ?, classificacao_risco = ?, peso_kg = ?, id_setor = ?, id_coleta = ? WHERE id_residuo = ?",
                            v[0], v[1], v[2], v[3], v[4], id), "Resíduo");
                    return um(c, SQL_RESIDUO + "WHERE r.id_residuo = ?", "Resíduo", id);
                }
                case "DELETE":
                    exigeId(id);
                    exigeLinha(executar(c, "DELETE FROM Residuo WHERE id_residuo = ?", id), "Resíduo");
                    return mapa("removido", true, "id_residuo", id);
                default: throw metodoErro();
            }
        }
    }

    /** Valida o corpo de um resíduo e devolve {nome, classificacao, peso, id_setor, id_coleta}. */
    private static Object[] validarResiduo(Connection c, Map<String, Object> b) throws SQLException {
        String nome = texto(b, "nome_residuo", 100, true);
        String grupo = texto(b, "classificacao_risco", 50, true);
        if (!GRUPO.matcher(grupo).matches())
            throw invalido("classificacao_risco deve começar com 'Grupo A' a 'Grupo E' (ex.: 'Grupo A - Biológico')");
        BigDecimal peso = decimal(b, "peso_kg");
        Integer setor = inteiro(b, "id_setor", true);
        Integer coleta = inteiro(b, "id_coleta", false);
        if (!existe(c, "SELECT 1 FROM Setor WHERE id_setor = ?", setor)) throw invalido("id_setor " + setor + " não existe");
        if (coleta != null && !existe(c, "SELECT 1 FROM Coleta WHERE id_coleta = ?", coleta)) throw invalido("id_coleta " + coleta + " não existe");
        return new Object[]{nome, grupo, peso, setor, coleta};
    }

    // ============================================================ Funcionários (derivados)
    private static Object funcionarios() throws SQLException {
        try (Connection c = conn()) {
            return consultar(c, "SELECT funcionario_responsavel AS nome, COUNT(*) AS total_coletas, MAX(data_hora) AS ultima_coleta "
                    + "FROM Coleta GROUP BY funcionario_responsavel ORDER BY funcionario_responsavel");
        }
    }

    // ============================================================ Saída de resíduo (transação)
    private static Object registrarSaida(Map<String, Object> b) throws SQLException {
        String nome = texto(b, "nome_residuo", 100, true);
        String grupo = texto(b, "classificacao_risco", 50, true);
        if (!GRUPO.matcher(grupo).matches()) throw invalido("classificacao_risco deve começar com 'Grupo A' a 'Grupo E'");
        BigDecimal peso = decimal(b, "peso_kg");
        Integer setor = inteiro(b, "id_setor", true);
        Integer descarte = inteiro(b, "id_descarte", true);
        String func = texto(b, "funcionario_responsavel", 100, true);
        Timestamp quando = dataHora(b);

        try (Connection c = conn()) {
            if (!existe(c, "SELECT 1 FROM Setor WHERE id_setor = ?", setor)) throw invalido("id_setor " + setor + " não existe");
            List<Map<String, Object>> d = consultar(c, "SELECT empresa_terceirizada FROM Descarte_Final WHERE id_descarte = ?", descarte);
            if (d.isEmpty()) throw invalido("id_descarte " + descarte + " não existe");
            String msg = incompatibilidade(grupo.charAt(6), String.valueOf(d.get(0).get("empresa_terceirizada")));
            if (msg != null) throw new ApiErro(422, msg);

            c.setAutoCommit(false);
            try {
                int idColeta = inserir(c, "INSERT INTO Coleta (data_hora, funcionario_responsavel, id_descarte) VALUES (?, ?, ?)", quando, func, descarte);
                int idResiduo = inserir(c, "INSERT INTO Residuo (nome_residuo, classificacao_risco, peso_kg, id_setor, id_coleta) VALUES (?, ?, ?, ?, ?)",
                        nome, grupo, peso, setor, idColeta);
                c.commit();
                return new Resposta(201, mapa("id_coleta", idColeta, "id_residuo", idResiduo, "peso_kg", peso, "mensagem", "Saída registrada"));
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Regra grupo ANVISA x método de tratamento (método = texto após " - " no nome da empresa). Retorna null se compatível. */
    private static String incompatibilidade(char grupo, String empresa) {
        String[] partes = empresa.split(" - ");
        String metodo = partes.length > 1 ? partes[partes.length - 1] : "";
        String m = metodo.toLowerCase();
        String aceitos; String nomeMetodo;
        if (m.contains("autoclav")) { aceitos = "A"; nomeMetodo = "Autoclavação"; }
        else if (m.contains("incinera")) { aceitos = "ABE"; nomeMetodo = "Incineração"; }
        else if (m.contains("industrial")) { aceitos = "B"; nomeMetodo = "Aterro Industrial"; }
        else if (m.contains("sanitário") || m.contains("sanitario")) { aceitos = "D"; nomeMetodo = "Aterro Sanitário"; }
        else if (m.contains("decaimento") || m.contains("radioativ")) { aceitos = "C"; nomeMetodo = "Decaimento Radioativo"; }
        else return null;   // método desconhecido: sem restrição
        if (aceitos.indexOf(grupo) >= 0) return null;
        return nomeMetodo + " não recebe resíduos do Grupo " + grupo + ". Grupos aceitos: " + String.join(", ", aceitos.split(""));
    }

    // ============================================================ Dashboard
    private static Object dashboard() throws SQLException {
        final String base = "FROM Residuo r JOIN Coleta c ON c.id_coleta = r.id_coleta ";
        try (Connection c = conn()) {
            Map<String, Object> hoje = consultar(c, "SELECT COALESCE(SUM(r.peso_kg),0) AS kg, COUNT(*) AS itens " + base + "WHERE DATE(c.data_hora) = CURDATE()").get(0);
            Map<String, Object> ontem = consultar(c, "SELECT COALESCE(SUM(r.peso_kg),0) AS kg, COUNT(*) AS itens " + base + "WHERE DATE(c.data_hora) = CURDATE() - INTERVAL 1 DAY").get(0);
            Object pendentes = consultar(c, "SELECT COUNT(*) AS n FROM Coleta WHERE data_hora >= CURDATE() - INTERVAL 1 DAY").get(0).get("n");
            List<Map<String, Object>> risco = consultar(c, "SELECT s.id_setor, s.nome_setor, SUM(r.peso_kg) AS kg_perigosos "
                    + base + "JOIN Setor s ON s.id_setor = r.id_setor "
                    + "WHERE r.classificacao_risco NOT LIKE 'Grupo D%' AND c.data_hora >= CURDATE() - INTERVAL 29 DAY "
                    + "GROUP BY s.id_setor, s.nome_setor ORDER BY kg_perigosos DESC LIMIT 1");
            List<Map<String, Object>> porGrupo = consultar(c, "SELECT SUBSTRING(r.classificacao_risco, 7, 1) AS grupo, SUM(r.peso_kg) AS kg, COUNT(*) AS itens "
                    + base + "WHERE c.data_hora >= CURDATE() - INTERVAL 29 DAY GROUP BY SUBSTRING(r.classificacao_risco, 7, 1) ORDER BY grupo");
            List<Map<String, Object>> serie = consultar(c, "SELECT DATE(c.data_hora) AS dia, SUBSTRING(r.classificacao_risco, 7, 1) AS grupo, SUM(r.peso_kg) AS kg "
                    + base + "WHERE c.data_hora >= CURDATE() - INTERVAL 13 DAY GROUP BY DATE(c.data_hora), SUBSTRING(r.classificacao_risco, 7, 1) ORDER BY dia, grupo");
            List<Map<String, Object>> ultimas = consultar(c, "SELECT r.id_residuo, r.nome_residuo, r.classificacao_risco, r.peso_kg, s.nome_setor, "
                    + "c.id_coleta, c.data_hora, c.funcionario_responsavel, d.empresa_terceirizada "
                    + base + "JOIN Setor s ON s.id_setor = r.id_setor LEFT JOIN Descarte_Final d ON d.id_descarte = c.id_descarte "
                    + "ORDER BY c.data_hora DESC, r.id_residuo DESC LIMIT 8");
            return mapa("coletado_hoje", hoje, "coletado_ontem", ontem, "coletas_pendentes", pendentes,
                    "setor_maior_risco", risco.isEmpty() ? null : risco.get(0), "por_grupo_30_dias", porGrupo,
                    "serie_14_dias", serie, "ultimas_movimentacoes", ultimas);
        }
    }

    // ============================================================ JDBC (auxiliares)
    private static Connection conn() throws SQLException {
        String url = System.getenv("DB_URL");
        if (url != null && !url.isBlank())
            return DriverManager.getConnection(url.trim(), env("DB_USER", "root"), env("DB_PASS", ""));
        return ConexaoDB.conectar();   // lança RuntimeException se falhar (tratado como 503)
    }

    private static void bind(PreparedStatement st, Object[] ps) throws SQLException {
        for (int i = 0; i < ps.length; i++) {
            Object v = ps[i];
            if (v == null) st.setObject(i + 1, null);
            else if (v instanceof Timestamp) st.setTimestamp(i + 1, (Timestamp) v);
            else st.setObject(i + 1, v);
        }
    }

    private static List<Map<String, Object>> consultar(Connection c, String sql, Object... ps) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            bind(st, ps);
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof Timestamp) v = ((Timestamp) v).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        else if (v instanceof java.sql.Date) v = v.toString();
                        row.put(md.getColumnLabel(i), v);
                    }
                    out.add(row);
                }
                return out;
            }
        }
    }

    private static Map<String, Object> um(Connection c, String sql, String rotulo, Object id) throws SQLException {
        List<Map<String, Object>> r = consultar(c, sql, id);
        if (r.isEmpty()) throw naoEncontrado(rotulo + " " + id + " não encontrado(a)");
        return r.get(0);
    }

    private static int executar(Connection c, String sql, Object... ps) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) { bind(st, ps); return st.executeUpdate(); }
    }

    private static int inserir(Connection c, String sql, Object... ps) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(st, ps);
            st.executeUpdate();
            try (ResultSet k = st.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
                throw new SQLException("O banco não devolveu o id gerado");
            }
        }
    }

    private static boolean existe(Connection c, String sql, Object id) throws SQLException { return !consultar(c, sql, id).isEmpty(); }
    private static void exigeLinha(int n, String rotulo) { if (n == 0) throw naoEncontrado(rotulo + " não encontrado(a)"); }
    private static void exigeId(Integer id) { if (id == null) throw invalido("Informe o id na URL (ex.: /api/recurso/1)"); }
    private static void exigeDescarte(Connection c, Integer id) throws SQLException {
        if (id != null && !existe(c, "SELECT 1 FROM Descarte_Final WHERE id_descarte = ?", id)) throw invalido("id_descarte " + id + " não existe");
    }

    // ============================================================ Validação de entrada
    private static String texto(Map<String, Object> b, String campo, int max, boolean obrigatorio) {
        Object v = b.get(campo);
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) { if (obrigatorio) throw invalido("Campo obrigatório: " + campo); return null; }
        if (s.length() > max) throw invalido("Campo " + campo + " excede " + max + " caracteres");
        return s;
    }

    private static Integer inteiro(Map<String, Object> b, String campo, boolean obrigatorio) {
        Object v = b.get(campo);
        if (v == null || String.valueOf(v).isBlank()) { if (obrigatorio) throw invalido("Campo obrigatório: " + campo); return null; }
        return parseInt(String.valueOf(v), campo);
    }

    private static int parseInt(String s, String campo) {
        try { return new BigDecimal(s.trim()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw invalido("Campo " + campo + " deve ser um número inteiro"); }
    }

    private static BigDecimal decimal(Map<String, Object> b, String campo) {
        Object v = b.get(campo);
        if (v == null) throw invalido("Campo obrigatório: " + campo);
        try {
            BigDecimal d = new BigDecimal(String.valueOf(v).trim().replace(',', '.')).setScale(2, java.math.RoundingMode.HALF_UP);
            if (d.signum() <= 0) throw invalido("Campo " + campo + " deve ser maior que zero");
            if (d.compareTo(new BigDecimal("99999999.99")) > 0) throw invalido("Campo " + campo + " excede o limite DECIMAL(10,2)");
            return d;
        } catch (NumberFormatException e) { throw invalido("Campo " + campo + " deve ser numérico"); }
    }

    private static Timestamp dataHora(Map<String, Object> b) {
        Object v = b.get("data_hora");
        if (v == null || String.valueOf(v).isBlank()) return Timestamp.valueOf(LocalDateTime.now().withNano(0));
        try { return Timestamp.valueOf(LocalDateTime.parse(String.valueOf(v).trim().replace(' ', 'T'))); }
        catch (Exception e) { throw invalido("data_hora inválida. Use o formato 2026-10-17T15:30:00"); }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new LinkedHashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return m;
        for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            try {
                m.put(java.net.URLDecoder.decode(kv[0], "UTF-8"), kv.length > 1 ? java.net.URLDecoder.decode(kv[1], "UTF-8") : "");
            } catch (java.io.UnsupportedEncodingException ignored) { }
        }
        return m;
    }

    // ============================================================ HTTP (entrada e saída)
    private static void cors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Map<String, Object> corpoJson(HttpExchange ex) throws IOException {
        byte[] raw = lerCorpo(ex.getRequestBody());
        if (raw.length == 0) throw invalido("Corpo da requisição vazio. Envie um JSON.");
        Object o;
        try { o = new Json(new String(raw, StandardCharsets.UTF_8)).lerTudo(); }
        catch (IllegalArgumentException e) { throw invalido("JSON inválido: " + e.getMessage()); }
        if (!(o instanceof Map)) throw invalido("O corpo deve ser um objeto JSON");
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        return m;
    }

    private static byte[] lerCorpo(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
            if (buf.size() > MAX_BODY) throw new ApiErro(413, "Corpo da requisição muito grande");
        }
        return buf.toByteArray();
    }

    private static void enviarJson(HttpExchange ex, int status, Object corpo) throws IOException {
        byte[] bytes = Json.escrever(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static int servirEstatico(HttpExchange ex, String path) throws IOException {
        String rel = path.equals("/") ? "index.html" : path.substring(1);
        Path arq = PASTA_FRONT.resolve(rel).normalize();
        if (!arq.startsWith(PASTA_FRONT)) { enviarJson(ex, 403, mapa("erro", "Acesso negado")); return 403; }
        if (Files.isDirectory(arq)) arq = arq.resolve("index.html");
        if (!Files.isRegularFile(arq)) {
            enviarJson(ex, 404, mapa("erro", "Arquivo não encontrado. Coloque o frontend em: " + PASTA_FRONT)); return 404;
        }
        byte[] bytes = Files.readAllBytes(arq);
        ex.getResponseHeaders().set("Content-Type", tipo(arq.getFileName().toString()));
        boolean head = ex.getRequestMethod().equalsIgnoreCase("HEAD");
        ex.sendResponseHeaders(200, head ? -1 : bytes.length);
        if (!head) try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        return 200;
    }

    private static String tipo(String nome) {
        String n = nome.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ============================================================ JSON mínimo (leitura e escrita)
    static final class Json {
        private final String s; private int i = 0;
        Json(String s) { this.s = s; }

        Object lerTudo() {
            Object v = valor(); espaco();
            if (i < s.length()) throw erro("conteúdo inesperado após o fim do JSON");
            return v;
        }
        private IllegalArgumentException erro(String m) { return new IllegalArgumentException(m + " (posição " + i + ")"); }
        private void espaco() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private char atual() { if (i >= s.length()) throw erro("fim inesperado"); return s.charAt(i); }

        private Object valor() {
            espaco();
            char c = atual();
            if (c == '{') return objeto();
            if (c == '[') return lista();
            if (c == '"') return texto();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return numero();
        }
        private Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>(); i++; espaco();
            if (atual() == '}') { i++; return m; }
            while (true) {
                espaco(); if (atual() != '"') throw erro("esperava nome de campo");
                String k = texto(); espaco();
                if (atual() != ':') throw erro("esperava ':'"); i++;
                m.put(k, valor()); espaco();
                char c = atual(); i++;
                if (c == '}') return m;
                if (c != ',') throw erro("esperava ',' ou '}'");
            }
        }
        private List<Object> lista() {
            List<Object> l = new ArrayList<>(); i++; espaco();
            if (atual() == ']') { i++; return l; }
            while (true) {
                l.add(valor()); espaco();
                char c = atual(); i++;
                if (c == ']') return l;
                if (c != ',') throw erro("esperava ',' ou ']'");
            }
        }
        private String texto() {
            StringBuilder sb = new StringBuilder(); i++;
            while (true) {
                char c = atual(); i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = atual(); i++;
                switch (e) {
                    case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break; case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break; case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw erro("escape \\u incompleto");
                        try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException x) { throw erro("escape \\u inválido"); }
                        i += 4; break;
                    case '"': case '\\': case '/': sb.append(e); break;
                    default: throw erro("escape inválido");
                }
            }
        }
        private Object numero() {
            int ini = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (ini == i) throw erro("valor inesperado");
            try { return new BigDecimal(s.substring(ini, i)); } catch (NumberFormatException e) { throw erro("número inválido"); }
        }

        static String escrever(Object o) { StringBuilder sb = new StringBuilder(); escrever(o, sb); return sb.toString(); }

        @SuppressWarnings("unchecked")
        private static void escrever(Object o, StringBuilder sb) {
            if (o == null) sb.append("null");
            else if (o instanceof Boolean) sb.append(o);
            else if (o instanceof BigDecimal) sb.append(((BigDecimal) o).toPlainString());
            else if (o instanceof Number) sb.append(o);
            else if (o instanceof Map) {
                sb.append('{'); boolean p = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    if (!p) sb.append(','); p = false;
                    aspas(e.getKey(), sb); sb.append(':'); escrever(e.getValue(), sb);
                }
                sb.append('}');
            } else if (o instanceof Iterable) {
                sb.append('['); boolean p = true;
                for (Object x : (Iterable<Object>) o) { if (!p) sb.append(','); p = false; escrever(x, sb); }
                sb.append(']');
            } else aspas(String.valueOf(o), sb);
        }
        private static void aspas(String t, StringBuilder sb) {
            sb.append('"');
            for (int k = 0; k < t.length(); k++) {
                char c = t.charAt(k);
                switch (c) {
                    case '"': sb.append("\\\""); break; case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break; case '\r': sb.append("\\r"); break; case '\t': sb.append("\\t"); break;
                    default: if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
            sb.append('"');
        }
    }
}
