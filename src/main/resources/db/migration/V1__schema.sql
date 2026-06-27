CREATE TABLE produto (
    id                 BIGSERIAL PRIMARY KEY,
    nome               VARCHAR(120) NOT NULL,
    categoria          VARCHAR(60)  NOT NULL,
    custo_unit         NUMERIC(10,2) NOT NULL,
    preco_unit         NUMERIC(10,2) NOT NULL,
    validade_dias      INT NOT NULL,
    transformavel      BOOLEAN NOT NULL DEFAULT FALSE,
    transform_destino  VARCHAR(120),
    combo_natural_id   BIGINT REFERENCES produto(id)
);

CREATE TABLE lote (
    id              BIGSERIAL PRIMARY KEY,
    produto_id      BIGINT NOT NULL REFERENCES produto(id),
    quantidade      INT NOT NULL,
    data_recebido   DATE NOT NULL,
    data_validade   DATE NOT NULL,
    local_gondola   VARCHAR(60)
);

CREATE TABLE volume (
    id                      BIGSERIAL PRIMARY KEY,
    produto_id              BIGINT NOT NULL REFERENCES produto(id),
    lote_id                 BIGINT NOT NULL REFERENCES lote(id),
    quantidade_atual        INT NOT NULL,
    capacidade_prateleira   INT NOT NULL,
    dias_na_prateleira      INT NOT NULL,
    percentual_ocupacao     NUMERIC(5,2),
    giro_estoque            NUMERIC(10,2),
    atualizado_em           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE recomendacao (
    id                  BIGSERIAL PRIMARY KEY,
    lote_id             BIGINT NOT NULL REFERENCES lote(id),
    rota                VARCHAR(20) NOT NULL,
    desconto_pct        INT,
    combo_produto_id    BIGINT REFERENCES produto(id),
    receita_recuperada  NUMERIC(10,2),
    perda_evitada       NUMERIC(10,2),
    racional            TEXT,
    criado_em           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE campanha (
    id                BIGSERIAL PRIMARY KEY,
    recomendacao_id   BIGINT NOT NULL REFERENCES recomendacao(id),
    canal_whatsapp    TEXT,
    canal_story       TEXT,
    canal_etiqueta    TEXT,
    enviada_em        TIMESTAMP
);

CREATE TABLE produto_transformado (
    id                BIGSERIAL PRIMARY KEY,
    recomendacao_id   BIGINT NOT NULL REFERENCES recomendacao(id),
    produto_novo      VARCHAR(120),
    ingredientes      TEXT,
    modo_preparo      TEXT,
    preco_sugerido    NUMERIC(10,2),
    argumento_venda   TEXT,
    gerado_em         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE impacto (
    id              BIGSERIAL PRIMARY KEY,
    kg_salvo        NUMERIC(10,2),
    co2_evitado     NUMERIC(10,2),
    valor_rs        NUMERIC(10,2),
    registrado_em   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE historico_resgate (
    id                    BIGSERIAL PRIMARY KEY,
    produto_id            BIGINT NOT NULL REFERENCES produto(id),
    lote_id               BIGINT REFERENCES lote(id),
    quantidade_em_risco   INT NOT NULL,
    rota_aplicada         VARCHAR(20),
    semana_ref            DATE NOT NULL,
    criado_em             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE sugestao_pedido (
    id                  BIGSERIAL PRIMARY KEY,
    produto_id          BIGINT NOT NULL REFERENCES produto(id),
    ocorrencias_risco   INT NOT NULL,
    ajuste_pct          INT NOT NULL,
    racional            TEXT,
    gerado_em           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_lote_data_validade ON lote(data_validade);
CREATE INDEX idx_historico_resgate_produto_semana ON historico_resgate(produto_id, semana_ref);
CREATE INDEX idx_sugestao_pedido_produto ON sugestao_pedido(produto_id);
