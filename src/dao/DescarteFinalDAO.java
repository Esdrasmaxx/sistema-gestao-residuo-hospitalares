package dao;

import model.Descarte_Final;
import util.ConexaoDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DescarteFinalDAO {

    // --- CREATE ---

    public void salvar(Descarte_Final descarte) {
        String sql = "INSERT INTO Descarte_Final (empresa_terceirizada, comprovante_conformidade) VALUES (?, ?)";

        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, descarte.getEmpresa_terceirizada());
            stmt.setString(2, descarte.getComprovante_conformidade());

            stmt.execute();
            System.out.println("Descarte Final salvo com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar o descarte: " + e.getMessage());
        }
    }

    // --- READ ---

    public List<Descarte_Final> listarTodos() {
        // 1. O comando SQL de busca geral
        String sql = "SELECT * FROM Descarte_Final";

        // 2. A criação da lista vazia na memória RAM
        List<Descarte_Final> listaDescartes = new ArrayList<>();

        // 3. Abertura da conexão e execução da consulta
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            // 4. O laço de repetição lendo linha por linha do disco
            while (rs.next()) {
                // 5. Instanciando uma "caixa" vazia para a linha atual
                Descarte_Final descarteTemp = new Descarte_Final();

                // 6. Extraindo os dados do MySQL e injetando no Java
                descarteTemp.setId_descarte(rs.getInt("id_descarte"));
                descarteTemp.setEmpresa_terceirizada(rs.getString("empresa_terceirizada"));
                descarteTemp.setComprovante_conformidade(rs.getString("comprovante_conformidade"));

                // 7. Guardando a caixa preenchida dentro da lista
                listaDescartes.add(descarteTemp);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar descartes: " + e.getMessage());
        }

        // 8. Devolvendo a lista completa para quem chamou o método
        return listaDescartes;
    }

    // --- UPDATE ---

    public void atualizar(Descarte_Final descarte) {
        // 1. O comando SQL de alteração com trava de segurança
        String sql = "UPDATE Descarte_Final SET empresa_terceirizada =  ?, comprovante_conformidade = ? WHERE id_descarte = ?";

        // 2. Abertura do canal e preparação da instrução
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. Injeção da carga útil (Os novos dados)
            stmt.setString(1, descarte.getEmpresa_terceirizada());
            stmt.setString(2, descarte.getComprovante_conformidade());

            // 4. Injeção do alvo (A chave de busca)
            stmt.setInt(3, descarte.getId_descarte());

            // 5. Disparo da ordem para o disco rígido
            stmt.execute();
            System.out.println("Descarte Final atualizado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao atualizar o descarte: " + e.getMessage());
        }
    }

    // --- DELETE ---

    public void deletar(int id) {
        // 1. O comando SQL destrutivo com trava de segurança
        String sql = "DELETE FROM Descarte_Final WHERE id_descarte = ?";

        // 2. Abertura do túnel de rede e preparação da instrução
        try (Connection conn = ConexaoDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 3. Injeção da Chave Primária (O alvo)
            stmt.setInt(1, id);

            // 4. Disparo da ordem de exclusão
            stmt.execute();
            System.out.println("Descarte Final deletado com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao deletar o descarte: " + e.getMessage());
        }
    }
}