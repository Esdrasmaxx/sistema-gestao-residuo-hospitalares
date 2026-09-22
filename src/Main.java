import dao.ResiduoDAO;
import model.Residuo;

public class Main {
    public static void main(String[] args) {
        // 1. O Acionamento do Motor
        ResiduoDAO residuoDAO = new ResiduoDAO();
        Residuo novoResiduo = new Residuo();

        // 2. A Injeção de Carga Útil e Precisão Decimal
        novoResiduo.setNome("Seringas Usadas");
        novoResiduo.setClassificacao_risco("Infectante");
        novoResiduo.setPeso_kg(2.5);

        // 3. A Amarração Relacional Dupla
        novoResiduo.setId_setor(1);  // ID que você gerou no teste do Setor
        novoResiduo.setId_coleta(3); // ID exato que o banco devolveu na sua última execução

        // 4. O Disparo Físico
        System.out.println("Disparando pacote de Resíduo para o servidor...");
        residuoDAO.salvar(novoResiduo);

        // 5. A Leitura Cruzada de Confirmação
        System.out.println("\nLendo rastreio final do banco de dados:");
        for (Residuo r : residuoDAO.listarTodos()) {
            System.out.println("ID: " + r.getId_residuo() +
                    " | Lixo: " + r.getNome() +
                    " (" + r.getPeso_kg() + "kg)" +
                    " | FK Setor: " + r.getId_setor() +
                    " | FK Coleta: " + r.getId_coleta());
        }
    }
}