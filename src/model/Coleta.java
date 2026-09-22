package model;

import java.time.LocalDateTime;

public class Coleta {
    private int id_coleta;
    private LocalDateTime data_hora;
    private String funcionario_responsavel;
    private int id_descarte;

    // Construtor vazio
    public Coleta() {}

    // Construtor completo
    public Coleta(int id_coleta, LocalDateTime data_hora, String funcionario_responsavel, int id_descarte) {
        this.id_coleta = id_coleta;
        this.data_hora = data_hora;
        this.funcionario_responsavel = funcionario_responsavel;
        this.id_descarte = id_descarte;
    }

    // Getters e Setters
    public int getId_coleta() {
        return id_coleta;
    }

    public void setId_coleta(int id_coleta) {
        this.id_coleta = id_coleta;
    }

    public LocalDateTime getData_hora() {
        return data_hora;
    }

    public void setData_hora(LocalDateTime data_hora) {
        this.data_hora = data_hora;
    }

    public String getFuncionario_responsavel() {
        return funcionario_responsavel;
    }

    public void setFuncionario_responsavel(String funcionario_responsavel) {
        this.funcionario_responsavel = funcionario_responsavel;
    }

    public int getId_descarte() {
        return id_descarte;
    }

    public void setId_descarte(int id_descarte) {
        this.id_descarte = id_descarte;
    }
}