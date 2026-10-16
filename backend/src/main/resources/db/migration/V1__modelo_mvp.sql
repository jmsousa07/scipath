-- V1 — Modelo de dados do MVP do SciPath.
--
-- Fonte: DRS §11 (modelo conceitual de domínio) + ADR-0003 (conta, verificação de
-- e-mail, token hasheado), ADR-0005 (file_asset, estados de publicação, retenção) e
-- ADR-0006 (taxonomia de referência por seed).
--
-- Migração ADITIVA e IMUTÁVEL (RNF028): depois de aplicada, nunca editar este arquivo.
-- Qualquer correção de schema — inclusive desativar item de taxonomia — entra como
-- V2, V3, ... Ver o procedimento de reversão em ./README.md.
--
-- Decisão de modelagem (não óbvia, registrada aqui de propósito):
--   A relação material ↔ file_asset é dirigida por `material.file_asset_id`
--   (NOT NULL + UNIQUE, logo 1:1 no MVP), e NÃO por `file_asset.material_id`.
--   Motivo: o arquivo precisa existir sozinho enquanto é validado ou quando o
--   material ainda não foi gravado — é assim que o ADR-0005 §2 trata o arquivo
--   "órfão" do coletor de limpeza. Com a FK no sentido material → file_asset a
--   órfandade é expressa por ausência de referência, sem FK circular nem coluna
--   anulável no lado que o RN004 exige preenchido.
--
-- Sem RASCUNHO: o card E1.2 fixa o enum em PUBLICADO | RETIRADO | EXCLUIDO
-- (ADR-0005 §3). Sem rascunho, todo material tem arquivo desde o nascimento, o que
-- é o que permite o NOT NULL de file_asset_id abaixo (RN004).

-- gen_random_uuid() é core do PostgreSQL desde a versão 13 — sem extensão extra.

-- =====================================================================
-- Taxonomia de referência (ADR-0006). Populada pela seed V2 (card E1.3).
-- =====================================================================

CREATE TABLE category (
    id        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      text        NOT NULL,
    slug      text        NOT NULL,
    descricao text,
    parent_id uuid        REFERENCES category (id),
    ativo     boolean     NOT NULL DEFAULT true,
    CONSTRAINT category_slug_unico    UNIQUE (slug),
    CONSTRAINT category_slug_formato  CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT category_sem_auto_pai  CHECK (parent_id IS NULL OR parent_id <> id)
);

COMMENT ON TABLE  category           IS 'Categoria científica de referência (RF018, RN005). Alteração é migração Flyway, não operação de tela.';
COMMENT ON COLUMN category.slug      IS 'Chave estável guardada e enviada pelo cliente; o nome de exibição pode mudar sem quebrar dados (ADR-0006).';
COMMENT ON COLUMN category.descricao IS 'Descrição curta para descoberta e SEO (DRS §12). Opcional na coluna; a seed V2 preenche.';
COMMENT ON COLUMN category.parent_id IS 'Hierarquia simples. NULL = raiz. O MVP usa um nível só (ADR-0006); profundidade maior não tem requisito.';
COMMENT ON COLUMN category.ativo     IS 'Item ativo. Inativo deixa de aparecer nas listas públicas (default true).';

CREATE TABLE material_type (
    id    uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    nome  text    NOT NULL,
    slug  text    NOT NULL,
    ativo boolean NOT NULL DEFAULT true,
    CONSTRAINT material_type_slug_unico   UNIQUE (slug),
    CONSTRAINT material_type_slug_formato CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

COMMENT ON TABLE  material_type      IS 'Tipo de material de referência (RF018) — nota de aula, slides, artigo, guia etc.';
COMMENT ON COLUMN material_type.slug IS 'Chave estável (ADR-0006).';
COMMENT ON COLUMN material_type.ativo IS 'Item ativo; inativos só aparecem com filtro explícito.';

CREATE TABLE license (
    id              uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            text    NOT NULL,
    slug            text    NOT NULL,
    identificador   text,
    url_referencia  text,
    descricao_curta text    NOT NULL,
    ativo           boolean NOT NULL DEFAULT true,
    CONSTRAINT license_slug_unico   UNIQUE (slug),
    CONSTRAINT license_slug_formato CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9.]+)*$')
);

COMMENT ON TABLE  license                 IS 'Licença de reutilização de referência (RF018, RN006). Sempre explícita: nunca inferir domínio público por o material estar público.';
COMMENT ON COLUMN license.slug            IS 'Chave estável (ADR-0006).';
COMMENT ON COLUMN license.identificador   IS 'Identificador público da licença (ex.: SPDX). NULL quando não existe — caso de "Todos os direitos reservados".';
COMMENT ON COLUMN license.url_referencia  IS 'URL do texto de referência da licença. NULL quando não há texto público.';
COMMENT ON COLUMN license.descricao_curta IS 'Explicação em linguagem simples do que a licença permite (RN006, DRS §13.2).';
COMMENT ON COLUMN license.ativo           IS 'Item ativo; inativos não são oferecidos na publicação.';

-- Índices de listagem: as três listas de taxonomia são servidas ordenadas por nome
-- e filtradas por ativo (contrato v0.2).
CREATE INDEX category_listagem_idx      ON category (ativo, nome);
CREATE INDEX material_type_listagem_idx ON material_type (ativo, nome);
CREATE INDEX license_listagem_idx       ON license (ativo, nome);

-- =====================================================================
-- Conta e identidade (ADR-0003)
-- =====================================================================

CREATE TABLE account (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email               text        NOT NULL,
    senha_hash          text        NOT NULL,
    estado              text        NOT NULL DEFAULT 'PENDENTE_VERIFICACAO',
    email_verificado_em timestamptz,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT account_email_unico       UNIQUE (email),
    CONSTRAINT account_email_normalizado CHECK (email = lower(email) AND email = btrim(email)),
    CONSTRAINT account_estado_valido     CHECK (estado IN ('PENDENTE_VERIFICACAO', 'ATIVO', 'SUSPENSO'))
);

COMMENT ON TABLE  account                     IS 'Conta de autenticação (DRS §11.1.1). Separada do profile de propósito: dado privado não vaza por engano (RNF013).';
COMMENT ON COLUMN account.email               IS 'E-mail de login, normalizado (minúsculas, sem espaços). UNIQUE é a invariante de unicidade de identidade (RN009, DRS §11.3).';
COMMENT ON COLUMN account.senha_hash          IS 'Hash bcrypt custo 12 (ADR-0003). Nunca registrar senha ou hash em log (RNF009).';
COMMENT ON COLUMN account.estado              IS 'PENDENTE_VERIFICACAO (inicial) | ATIVO (após confirmar o e-mail) | SUSPENSO (abuso). Login funciona em PENDENTE_VERIFICACAO; só publicar exige ATIVO.';
COMMENT ON COLUMN account.email_verificado_em IS 'Momento da confirmação do e-mail. NULL enquanto não verificado — é o que libera a publicação.';
COMMENT ON COLUMN account.atualizado_em       IS 'Atualizado pela aplicação a cada escrita. Não há trigger: a regra de negócio fica no serviço.';

CREATE TABLE verification_token (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id   uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash   text        NOT NULL,
    tipo         text        NOT NULL,
    expira_em    timestamptz NOT NULL,
    consumido_em timestamptz,
    criado_em    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verification_token_hash_unico UNIQUE (token_hash),
    CONSTRAINT verification_token_tipo_valido CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA'))
);

COMMENT ON TABLE  verification_token              IS 'Token de uso único para confirmação de e-mail ou recuperação de senha (ADR-0003 (c) e (d)).';
COMMENT ON COLUMN verification_token.account_id   IS 'Dono do token. Apagar a conta leva os tokens junto.';
COMMENT ON COLUMN verification_token.token_hash   IS 'SHA-256 do token. O token em claro nunca é persistido nem registrado em log (ADR-0003 (c), RNF009). UNIQUE porque o mesmo token não pode valer duas vezes.';
COMMENT ON COLUMN verification_token.tipo         IS 'CONFIRMACAO_EMAIL (validade 24 h) | RECUPERACAO_SENHA (validade curta, 30 min).';
COMMENT ON COLUMN verification_token.expira_em    IS 'Instante em que o token deixa de valer, independente de consumo.';
COMMENT ON COLUMN verification_token.consumido_em IS 'Marco do uso único: preenchido na primeira confirmação. Não nulo = token queimado, mesmo antes de expirar.';

CREATE INDEX verification_token_conta_tipo_idx ON verification_token (account_id, tipo);

CREATE TABLE profile (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    nome_publico        text        NOT NULL,
    slug                text        NOT NULL,
    biografia           text,
    afiliacao_declarada text,
    areas_interesse     text[],
    links_externos      text[],
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT profile_conta_unica  UNIQUE (account_id),
    CONSTRAINT profile_slug_unico   UNIQUE (slug),
    CONSTRAINT profile_slug_formato CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

COMMENT ON TABLE  profile                     IS 'Identidade pública da conta (DRS §11.1.2). Uma conta tem um perfil (1:1 no MVP).';
COMMENT ON COLUMN profile.slug                IS 'Slug público único — a URL canônica do perfil (RN009, DRS §12.3).';
COMMENT ON COLUMN profile.afiliacao_declarada IS 'Afiliação DECLARADA pelo próprio autor. Não é vínculo verificado pela plataforma — nunca apresentar como tal (RN011, DRS §13.1).';
COMMENT ON COLUMN profile.areas_interesse     IS 'Lista de áreas de interesse (texto livre, controle no serviço). Sem requisito além da exibição.';
COMMENT ON COLUMN profile.links_externos      IS 'Links externos do autor (texto livre). Sem requisito de verificação no MVP.';
COMMENT ON COLUMN profile.biografia           IS 'Opcional — campo adicional (RN004 trata só os metadados do material como obrigatórios).';

-- =====================================================================
-- Arquivo armazenado (ADR-0005 §2, DRS §11.1.4)
-- =====================================================================

CREATE TABLE file_asset (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    chave_interna        text        NOT NULL,
    nome_original_seguro text        NOT NULL,
    tipo_detectado       text        NOT NULL,
    tamanho_bytes        bigint      NOT NULL,
    checksum             text        NOT NULL,
    estado_processamento text        NOT NULL DEFAULT 'PENDENTE',
    enviado_em           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT file_asset_chave_interna_unica UNIQUE (chave_interna),
    CONSTRAINT file_asset_tamanho_nao_negativo CHECK (tamanho_bytes >= 0),
    CONSTRAINT file_asset_estado_valido CHECK (estado_processamento IN ('PENDENTE', 'VALIDADO', 'ORFAO', 'PURGADO'))
);

COMMENT ON TABLE  file_asset                      IS 'Metadados do arquivo armazenado; o binário fica fora do banco e fora de diretório público (ADR-0005 §2).';
COMMENT ON COLUMN file_asset.chave_interna        IS 'Chave gerada pelo sistema (UUID). O nome original NUNCA decide o caminho no storage.';
COMMENT ON COLUMN file_asset.nome_original_seguro IS 'Nome original sanitizado, guardado só como metadado de exibição/download.';
COMMENT ON COLUMN file_asset.tipo_detectado       IS 'Tipo real do conteúdo (magic bytes %PDF-), nunca a extensão sozinha (ADR-0005 §1).';
COMMENT ON COLUMN file_asset.tamanho_bytes        IS 'Tamanho em bytes. Comparado ao limite de upload, que é configuração (SCIPATH_UPLOAD_MAX_BYTES).';
COMMENT ON COLUMN file_asset.checksum             IS 'SHA-256 do conteúdo, em hexadecimal minúsculo. Base de integridade da publicação (RN008).';
COMMENT ON COLUMN file_asset.estado_processamento IS 'PENDENTE (recebido, ainda não validado) | VALIDADO (conteúdo conferido, RN008) | ORFAO (gravado sem material — marcado para o coletor) | PURGADO (binário removido pelo coletor após a retenção; metadados permanecem).';
COMMENT ON COLUMN file_asset.enviado_em           IS 'Momento do recebimento. Base do cálculo de órfandade e de retenção do ADR-0005 §3.';

-- =====================================================================
-- Material publicado (DRS §11.1.3)
-- =====================================================================

CREATE TABLE material (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug              text        NOT NULL,
    titulo            text        NOT NULL,
    descricao         text        NOT NULL,
    profile_id        uuid        NOT NULL REFERENCES profile (id),
    category_id       uuid        NOT NULL REFERENCES category (id),
    material_type_id  uuid        NOT NULL REFERENCES material_type (id),
    license_id        uuid        NOT NULL REFERENCES license (id),
    file_asset_id     uuid        NOT NULL REFERENCES file_asset (id),
    estado_publicacao text        NOT NULL DEFAULT 'PUBLICADO',
    publicado_em      timestamptz NOT NULL DEFAULT now(),
    criado_em         timestamptz NOT NULL DEFAULT now(),
    atualizado_em     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT material_slug_unico    UNIQUE (slug),
    CONSTRAINT material_slug_formato  CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT material_arquivo_unico UNIQUE (file_asset_id),
    CONSTRAINT material_estado_valido CHECK (estado_publicacao IN ('PUBLICADO', 'RETIRADO', 'EXCLUIDO'))
);

COMMENT ON TABLE  material                   IS 'Recurso compartilhado (DRS §11.1.3). Um perfil pode ter zero ou muitos materiais; cada material tem um autor/proprietário.';
COMMENT ON COLUMN material.slug              IS 'Identificador público único — a URL canônica do material (RN009, DRS §11.3).';
COMMENT ON COLUMN material.titulo            IS 'Metadado obrigatório da publicação (RN004).';
COMMENT ON COLUMN material.descricao         IS 'Metadado obrigatório da publicação (RN004).';
COMMENT ON COLUMN material.profile_id        IS 'Proprietário/autor PRINCIPAL. Determinado no contexto autenticado, nunca pelo payload (RN001).';
COMMENT ON COLUMN material.category_id       IS 'FK para a categoria controlada. Categoria inexistente é recusada pelo banco (RN005) — não existe texto livre de categoria.';
COMMENT ON COLUMN material.material_type_id  IS 'FK para o tipo de material de referência.';
COMMENT ON COLUMN material.license_id        IS 'FK para a licença explícita. Toda publicação declara a condição de reuso (RN006).';
COMMENT ON COLUMN material.file_asset_id     IS 'Arquivo do material, 1:1 no MVP (índice UNIQUE). NOT NULL porque não existe material sem arquivo validado (RN004, RN008).';
COMMENT ON COLUMN material.estado_publicacao IS 'PUBLICADO (elegível a busca e acesso) | RETIRADO (fora do público, reversível) | EXCLUIDO (exclusão lógica; metadados retidos para auditoria, binário purgado após 7 dias). ADR-0005 §3.';
COMMENT ON COLUMN material.publicado_em      IS 'Data de publicação. Ordenação padrão da listagem pública: publicado_em DESC com desempate por id (DRS §12.2).';
COMMENT ON COLUMN material.criado_em         IS 'Data de criação do registro.';
COMMENT ON COLUMN material.atualizado_em     IS 'Atualizado pela aplicação a cada escrita (inclusive mudança de estado_publicacao).';

-- Índices de listagem/filtro do contrato (RF002): o estado decide a elegibilidade
-- pública (RN002/RN013), então a listagem pública filtra por estado e ordena por data.
-- categoria, tipo e autor são os outros filtros previstos.
CREATE INDEX material_estado_data_idx ON material (estado_publicacao, publicado_em DESC, id DESC);
CREATE INDEX material_categoria_idx   ON material (category_id, estado_publicacao, publicado_em DESC);
CREATE INDEX material_tipo_idx        ON material (material_type_id, estado_publicacao, publicado_em DESC);
CREATE INDEX material_autor_idx       ON material (profile_id, estado_publicacao);
