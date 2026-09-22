package dao;

import model.Coleta;
import util.ConexaoDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ColetaDAO {

    // --- CREATE ---

    public void salvar(Coleta coleta) {
        // 1. A instrução SQL com três lacunas, incluindo a Chave Estrangeira
        String sql = "INSERT INTO Coleta (data_hora, funcionario_responsavel, id_descarte) VALUES (?, ?, ?)";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 2. O Motor de Conversão Reverso (Java -> MySQL)
            if (coleta.getData_hora() != null) {
                stmt.setTimestamp(1, Timestamp.valueOf(coleta.getData_hora()));
            } else {
                stmt.setNull(1, java.sql.Types.TIMESTAMP);
            }

            // 3. Injeção de texto blindado
            stmt.setString(2, coleta.getFuncionario_responsavel());

            // 4. Injeção do vínculo relacional (FK)
            stmt.setInt(3, coleta.getId_descarte());

            // 5. Disparo físico para o banco
            stmt.execute();
            System.out.println("Coleta salva com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar a coleta: " + e.getMessage());
        }
    }

    // --- READ ---

    public List<Coleta> listarTodos() {
        String sql = "SELECT * FROM Coleta";
        List<Coleta> listaColetas = new ArrayList<>();

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Coleta coletaTemp = new Coleta();

                coletaTemp.setId_coleta(rs.getInt("id_coleta"));
                coletaTemp.setFuncionario_responsavel(rs.getString("funcionario_responsavel"));

                // Tratamento da Chave Estrangeira (FK)
                coletaTemp.setId_descarte(rs.getInt("id_descarte"));

                // O Motor de Conversão de Tempo
                Timestamp tempoDoBanco = rs.getTimestamp("data_hora");
                if (tempoDoBanco != null) {
                    coletaTemp.setData_hora(tempoDoBanco.toLocalDateTime());
                }

                listaColetas.add(coletaTemp);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar coletas: " + e.getMessage());
        }

        return listaColetas;
    }

    // --- UPDATE ---
    public void atualizar(Coleta coleta) {
        // 1. A instrução de alteração múltipla com a trava obrigatória
        String sql = "UPDATE Coleta SET data_hora = ?, funcionario_responsavel = ?, id_descarte = ? WHERE id_coleta = ?";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 2. O Motor de Conversão Reverso (Índice 1)
            if (coleta.getData_hora() != null) {
                stmt.setTimestamp(1, Timestamp.valueOf(coleta.getData_hora()));
            } else {
                stmt.setNull(1, java.sql.Types.TIMESTAMP);
            }

            // 3. Injeção de dados normais e Chave Estrangeira (Índices 2 e 3)
            stmt.setString(2, coleta.getFuncionario_responsavel());
            stmt.setInt(3, coleta.getId_descarte());

            // 4. A Mira do Laser / Chave Primária (Índice 4)
            stmt.setInt(4, coleta.getId_coleta());

            // 5. O Disparo Físico
            stmt.execute();
            System.out.println("Coleta atualizada com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao atualizar a coleta: " + e.getMessage());
        }
    }

    // --- DELETE ---

    public void deletar(int id) {
        // 1. A instrução destrutiva com a trava de segurança
        String sql = "DELETE FROM Coleta WHERE id_coleta = ?";

        // 2. Abertura do túnel TCP/IP e preparação da blindagem
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. O mapeamento numérico do alvo
            stmt.setInt(1, id);

            // 4. A aniquilação física no servidor
            stmt.execute();
            System.out.println("Coleta deletada com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao deletar a coleta: " + e.getMessage());
        }
    }


}