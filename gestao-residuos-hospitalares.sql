CREATE DATABASE IF NOT EXISTS gestao_residuos;
USE gestao_residuos;

-- 2. Criação das Tabelas "Pai" (Sem chaves estrangeiras)
CREATE TABLE Setor (
    id_setor INT AUTO_INCREMENT PRIMARY KEY,
    nome_setor VARCHAR(100) NOT NULL
);

CREATE TABLE Descarte_Final (
    id_descarte INT AUTO_INCREMENT PRIMARY KEY,
    empresa_terceirizada VARCHAR(100) NOT NULL,
    comprovante_conformidade VARCHAR(100)
);

-- 3. Criação da Tabela Intermediária (Recebe FK do Descarte)
CREATE TABLE Coleta (
    id_coleta INT AUTO_INCREMENT PRIMARY KEY,
    data_hora DATETIME NOT NULL,
    funcionario_responsavel VARCHAR(100) NOT NULL,
    id_descarte INT,
    FOREIGN KEY (id_descarte) REFERENCES Descarte_Final(id_descarte)
);

-- 4. Criação da Tabela Central (Recebe FKs de Setor e Coleta)
CREATE TABLE Residuo (
    id_residuo INT AUTO_INCREMENT PRIMARY KEY,
    classificacao_risco VARCHAR(50) NOT NULL,
    peso_kg DECIMAL(10,2) NOT NULL,
    id_setor INT NOT NULL,
    id_coleta INT,
    FOREIGN KEY (id_setor) REFERENCES Setor(id_setor),
    FOREIGN KEY (id_coleta) REFERENCES Coleta(id_coleta)
);