package model;

public class Residuo {

    private String nome;
    private int id_residuo;
    private String classificacao_risco;
    private double peso_kg;
    private int id_setor;
    private int id_coleta;

    // Construtor vazio
    public Residuo() {}

    // Construtor completo
    public Residuo(int id_residuo, String classificacao_risco, double peso_kg, int id_setor, int id_coleta) {
        this.id_residuo = id_residuo;
        this.classificacao_risco = classificacao_risco;
        this.peso_kg = peso_kg;
        this.id_setor = id_setor;
        this.id_coleta = id_coleta;
    }

    // Getters e Setters
    public int getId_residuo() {
        return id_residuo;
    }

    public void setId_residuo(int id_residuo) {
        this.id_residuo = id_residuo;
    }

    public String getClassificacao_risco() {
        return classificacao_risco;
    }

    public void setClassificacao_risco(String classificacao_risco) {
        this.classificacao_risco = classificacao_risco;
    }

    public double getPeso_kg() {
        return peso_kg;
    }

    public void setPeso_kg(double peso_kg) {
        this.peso_kg = peso_kg;
    }

    public int getId_setor() {
        return id_setor;
    }

    public void setId_setor(int id_setor) {
        this.id_setor = id_setor;
    }

    public int getId_coleta() {
        return id_coleta;
    }

    public void setId_coleta(int id_coleta) {
        this.id_coleta = id_coleta;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}