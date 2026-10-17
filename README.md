# 🏥 Sistema de Gestão e Rastreabilidade de Resíduos Hospitalares

Projeto desenvolvido para a atividade A3 da Universidade Anhembi Morumbi, na disciplina de Banco de Dados. O objetivo é atuar como uma solução tecnológica sustentável para o rastreio seguro de resíduos perigosos desde a sua origem até o descarte final.

## 🛠️ Tecnologias Utilizadas
* **Banco de Dados:** MySQL (SGBD Relacional)
* **Back-end:** Java
* **Front-end:** HTML5 / CSS3 / Bootstrap
* **Ferramentas de Modelagem:** Draw.io

## 📊 Modelagem do Banco de Dados (DER)
O sistema é composto por 4 tabelas principais interligadas para garantir a integridade referencial:
1. **Setor:** Origem do resíduo (ex: UTI, Centro Cirúrgico).
2. **Residuo:** O material gerado, contendo peso e classificação de risco.
3. **Coleta:** O lote de movimentação interna e o funcionário responsável.
4. **Descarte_Final:** A empresa terceirizada e o comprovante de conformidade regulatória.

## 📂 Estrutura do Repositório
* `/database` - Contém o script SQL estrutural (`schema.sql`).
* `/backend` - Lógica em Java (em desenvolvimento).
* `/frontend` - Interfaces web de cadastro e relatórios.

---
Desenvolvido por **Esdras Maxsuel** e equipe.
