-- V4 — Sessão de identidade (card E3.3; ADR-0003 (e); contrato v0.4, tag `identidade`).
--
-- A sessão vive em TABELA, e não na memória do processo. O contrato v0.4 fixa isso
-- ("Onde a sessão vive") e o motivo é verificável: revogação que sobrevive ao reinício do
-- processo (é o que o logout precisa garantir), invalidação de todas as sessões de uma conta
-- de uma vez (recuperação de senha, E3.4) e nada de sessão presa a uma instância. O custo
-- aceito é uma consulta por requisição autenticada — é o preço da revogação real.
--
-- Migração ADITIVA e IMUTÁVEL (RNF028): depois de aplicada, nunca editar este arquivo.

CREATE TABLE sessao (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash    text        NOT NULL,
    csrf_token    text        NOT NULL,
    criada_em     timestamptz NOT NULL DEFAULT now(),
    ultimo_uso_em timestamptz NOT NULL DEFAULT now(),
    expira_em     timestamptz NOT NULL,
    CONSTRAINT sessao_token_hash_unico UNIQUE (token_hash),
    CONSTRAINT sessao_expira_depois_de_criar CHECK (expira_em > criada_em)
);

COMMENT ON TABLE  sessao               IS 'Sessão de identidade, no servidor e revogável (ADR-0003 (e), contrato v0.4). Sem JWT: o cliente guarda só a cookie opaca.';
COMMENT ON COLUMN sessao.account_id    IS 'Conta dona da sessão. Apagar a conta leva as sessões junto (ON DELETE CASCADE) — sessão órfã seria identidade sem dono.';
COMMENT ON COLUMN sessao.token_hash    IS 'SHA-256 (hex) do valor que viaja na cookie SCIPATH_SESSAO. O valor em claro NUNCA é persistido nem registrado em log (ADR-0003 (c), RNF009): vazamento da tabela não entrega sessão utilizável.';
COMMENT ON COLUMN sessao.csrf_token    IS 'Token CSRF da sessão, que viaja na cookie XSRF-TOKEN — legível pelo frontend da mesma origem, de propósito, porque é o cliente que o devolve no cabeçalho X-CSRF-Token. Não é credencial de acesso: sozinho não autentica nada, e é por isso que aqui ele fica em claro (ao contrário do token de sessão, que fica só como hash).';
COMMENT ON COLUMN sessao.criada_em     IS 'Instante da abertura da sessão. Base do limite absoluto (expira_em), que não se estende com o uso.';
COMMENT ON COLUMN sessao.ultimo_uso_em IS 'Instante do último uso aceito. Base da expiração por inatividade (12 h por padrão): o uso desloca o prazo, o limite absoluto não.';
COMMENT ON COLUMN sessao.expira_em     IS 'Limite absoluto de vida da sessão (7 dias por padrão). Passado ele, a requisição com esta cookie responde 401 — mesmo com uso recente.';

-- A resolução é por token_hash (UNIQUE, já indexado). O índice por conta serve ao E3.4
-- ("invalide todas as sessões desta conta") e à limpeza; o de expiração, à varredura de
-- sessões vencidas.
CREATE INDEX sessao_conta_idx      ON sessao (account_id);
CREATE INDEX sessao_expiracao_idx  ON sessao (expira_em);
