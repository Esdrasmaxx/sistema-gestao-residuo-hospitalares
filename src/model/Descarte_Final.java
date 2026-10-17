package model;

public class Descarte_Final {
    private int id_descarte;
    private String empresa_terceirizada;
    private String comprovante_conformidade;

    // Construtor vazio
    public Descarte_Final() {}

    // Construtor completo
    public Descarte_Final(int id_descarte, String empresa_terceirizada, String comprovante_conformidade) {
        this.id_descarte = id_descarte;
        this.empresa_terceirizada = empresa_terceirizada;
        this.comprovante_conformidade = comprovante_conformidade;
    }

    // Getters e Setters
    public int getId_descarte() {
        return id_descarte;
    }

    public void setId_descarte(int id_descarte) {
        this.id_descarte = id_descarte;
    }

    public String getEmpresa_terceirizada() {
        return empresa_terceirizada;
    }

    public void setEmpresa_terceirizada(String empresa_terceirizada) {
        this.empresa_terceirizada = empresa_terceirizada;
    }

    public String getComprovante_conformidade() {
        return comprovante_conformidade;
    }

    public void setComprovante_conformidade(String comprovante_conformidade) {
        this.comprovante_conformidade = comprovante_conformidade;
    }
}