package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoDB {

    private static final String URL = "jdbc:mysql://localhost:3306/gestao_residuos  ";
    private static final String USER = "root";
    private static final String PASSWORD = "@MACmilk123";

    public static Connection conectar() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Erro catastrófico ao conectar com o banco: " + e.getMessage());
        }
    }
}