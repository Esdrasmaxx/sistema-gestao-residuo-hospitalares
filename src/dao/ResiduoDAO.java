package dao;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Residuo;
import util.ConexaoDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ResiduoDAO {

    // --- CREATE ---
    public void salvar(Residuo residuo) {
        // 1. A instrução atualizada com as 5 colunas exatas do banco
        String sql = "INSERT INTO Residuo (nome, classificacao_risco, peso_kg, id_setor, id_coleta) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 2. Injeção dos dados textuais (Índices 1 e 2)
            stmt.setString(1, residuo.getNome());
            stmt.setString(2, residuo.getClassificacao_risco());

            // 3. Injeção da grandeza física (Índice 3)
            stmt.setDouble(3, residuo.getPeso_kg());

            // 4. Injeção das chaves relacionais / FKs (Índices 4 e 5)
            stmt.setInt(4, residuo.getId_setor());
            stmt.setInt(5, residuo.getId_coleta());

            // 5. Disparo da ordem magnética
            stmt.execute();
            System.out.println("Resíduo salvo com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar o resíduo: " + e.getMessage());
        }
    }

    // --- READ ---

    public List<Residuo> listarTodos() {
        // 1. O comando SQL de varredura total
        String sql = "SELECT * FROM Residuo";
        List<Residuo> listaResiduos = new ArrayList<>();

        // 2. Abertura do canal e disparo imediato da consulta
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            // 3. O cursor de leitura magnética
            while (rs.next()) {
                Residuo residuoTemp = new Residuo();

                // 4. Extração e mapeamento dos dados físicos e textuais
                residuoTemp.setId_residuo(rs.getInt("id_residuo"));
                residuoTemp.setNome(rs.getString("nome"));
                residuoTemp.setClassificacao_risco(rs.getString("classificacao_risco"));
                residuoTemp.setPeso_kg(rs.getDouble("peso_kg"));

                // 5. Captura das Chaves Estrangeiras (Vínculos)
                residuoTemp.setId_setor(rs.getInt("id_setor"));
                residuoTemp.setId_coleta(rs.getInt("id_coleta"));

                // 6. Empilhamento na memória RAM
                listaResiduos.add(residuoTemp);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar resíduos: " + e.getMessage());
        }

        return listaResiduos;
    }

    // --- UPDATE ---

    public void atualizar(Residuo residuo) {
        // 1. A instrução de alteração múltipla com trava de segurança (WHERE)
        String sql = "UPDATE Residuo SET nome = ?, classificacao_risco = ?, peso_kg = ?, id_setor = ?, id_coleta = ? WHERE id_residuo = ?";

        // 2. Abertura do canal e preparação da instrução
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. Injeção dos dados textuais (Índices 1 e 2)
            stmt.setString(1, residuo.getNome());
            stmt.setString(2, residuo.getClassificacao_risco());

            // 4. Injeção da grandeza física (Índice 3)
            stmt.setDouble(3, residuo.getPeso_kg());

            // 5. Injeção das chaves relacionais / FKs (Índices 4 e 5)
            stmt.setInt(4, residuo.getId_setor());
            stmt.setInt(5, residuo.getId_coleta());

            // 6. A Mira do Laser / Chave Primária (Índice 6)
            stmt.setInt(6, residuo.getId_residuo());

            // 7. O Disparo Físico para o servidor
            stmt.execute();
            System.out.println("Resíduo atualizado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao atualizar o resíduo: " + e.getMessage());
        }
    }

    // --- DELETE ---

    public void deletar(int id) {
        // 1. O comando irreversível com a trava de segurança (WHERE)
        String sql = "DELETE FROM Residuo WHERE id_residuo = ?";

        // 2. Abertura do túnel TCP/IP e preparação da blindagem
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. O mapeamento numérico do alvo (Chave Primária)
            stmt.setInt(1, id);

            // 4. A aniquilação física no servidor
            stmt.execute();
            System.out.println("Resíduo deletado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao deletar o resíduo: " + e.getMessage());
        }
    }
}