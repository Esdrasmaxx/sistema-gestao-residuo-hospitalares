package dao;

import model.Setor;
import util.ConexaoDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SetorDAO {

    // --- CREATE ---

    public void salvar(Setor setor) {
        // 1. O comando SQL parametrizado (proteção contra SQL Injection)
        String sql = "INSERT INTO Setor (nome_setor) VALUES (?)";

        // 2. Abertura da conexão automatizada (Try-with-Resources)
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. Substituição dos parâmetros
            stmt.setString(1, setor.getNome_setor());

            // 4. Execução no banco
            stmt.execute();
            System.out.println("Setor salvo com sucesso no banco de dados!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar o setor: " + e.getMessage());
        }
    }

    // --- READ ---

    public List<Setor> listarTodos() {
        String sql = "SELECT * FROM Setor";
        List<Setor> listaSetores = new ArrayList<>();

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Setor setorTemporario = new Setor();
                setorTemporario.setId_setor(rs.getInt("id_setor"));
                setorTemporario.setNome_setor(rs.getString("nome_setor"));

                listaSetores.add(setorTemporario);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar setores: " + e.getMessage());
        }

        return listaSetores;
    }

    // --- UPDATE ---

    public void atualizar(Setor setor) {
        // 1. O comando SQL de atualização com a trava de segurança (WHERE)
        String sql = "UPDATE Setor SET nome_setor = ? WHERE id_setor = ?";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 2. Preenchendo os parâmetros na exata ordem dos pontos de interrogação
            stmt.setString(1, setor.getNome_setor());
            stmt.setInt(2, setor.getId_setor());

            // 3. Executando a alteração no banco
            stmt.execute();
            System.out.println("Setor atualizado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao atualizar o setor: " + e.getMessage());
        }
    }

    // --- DELETE ---

    public void deletar(int id) {
        // 1. O comando SQL de exclusão cirúrgica
        String sql = "DELETE FROM Setor WHERE id_setor = ?";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 2. Injetando a chave de busca
            stmt.setInt(1, id);

            // 3. Executando a exclusão
            stmt.execute();
            System.out.println("Setor deletado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao deletar o setor: " + e.getMessage());
        }
    }
}