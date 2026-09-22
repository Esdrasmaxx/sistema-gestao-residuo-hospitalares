package model;

public class Setor {
    private int id_setor;
    private String nome_setor;

    // Construtor vazio (necessário para criar o objeto antes de ter os dados)
    public Setor() {}

    // Construtor completo
    public Setor(int id_setor, String nome_setor) {
        this.id_setor = id_setor;
        this.nome_setor = nome_setor;
    }

    // Getters e Setters (Portas de entrada e saída dos dados)
    public int getId_setor() {
        return id_setor;
    }

    public void setId_setor(int id_setor) {
        this.id_setor = id_setor;
    }

    public String getNome_setor() {
        return nome_setor;
    }

    public void setNome_setor(String nome_setor) {
        this.nome_setor = nome_setor;
    }
}