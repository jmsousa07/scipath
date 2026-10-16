# Arquitetura do SciPath (MVP)

> **Fonte #1:** `assets/raw_files/drs_scipath_v1.0.pdf`. Este documento **traduz** as diretrizes
> do DRS (§11 domínio, §12 busca/SEO, §14 arquitetura, §15 páginas e estados, §17 testes) para o
> desenho físico do repositório. O DRS é conceitual e neutro quanto a stack (§14.1); aqui ficam
> as decisões de implementação, cada uma registrada em ADR.
>
> **Status da stack (ADR-0001):** monorepo + Java 21/Spring Boot 3 + PostgreSQL + Next.js —
> **proposta registrada, aguardando ratificação do dono** (§20.3 item 1). Nenhuma funcionalidade
> de produto deve ser implementada antes dessa ratificação (Etapa 0 do roadmap).
>
> **Autenticação (ADR-0003 — decidida):** e-mail + senha, com verificação de e-mail
> **obrigatória para publicar**; navegar e editar perfil liberados sem verificação.
> Ver `docs/adr/ADR-0003-autenticacao-email-senha.md`.

## 1. Forma

```
backend/     Java 21 + Spring Boot 3 (Maven)   regras, autenticação, autorização, API REST
frontend/    Next.js (App Router) + TS + Tailwind   público indexável + workspace autenticado
e2e/         Playwright                         jornadas ponta a ponta
contracts/   openapi.yaml + db/migrations/      as duas fronteiras
docs/        visão, arquitetura, ADRs, cards, evidências de QA
infra/       scripts de ambiente (banco, setup)
assets/      design systems (contrato visual)
```

**Fronteira 1 — API** (`contracts/openapi.yaml`): documentada, com erros consistentes,
paginação e filtros uniformes, recursos públicos e privados distinguidos, sem expor entidades de
persistência (§14.3). Prefixo único (`/api`), decidido aqui e não reinventado em cada tela.

**Fronteira 2 — dados** (`contracts/db/migrations/`): migrações versionadas e imutáveis
(RNF028). O front nunca conhece o banco.

## 2. Modelo de dados (v1, derivado do §11)

O DRS é um modelo **conceitual**; a modelagem física preserva conceitos e invariantes.

```
account          id, email (único, normalizado), senha_hash (bcrypt 12), estado
                 (PENDENTE_VERIFICACAO | ATIVO | SUSPENSO), email_verificado_em,
                 criado_em, atualizado_em
verification_token  id, account_id, token_hash (SHA-256), tipo (CONFIRMACAO_EMAIL |
                 RECUPERACAO_SENHA), expira_em, consumido_em   # uso único, nunca em log
profile          id, account_id (1:1), nome_publico, slug (único), biografia,
                 afiliacao_declarada (opcional), areas_interesse[], links_externos[],
                 criado_em, atualizado_em
category         id, nome, slug (único), descricao (opcional), parent_id (opcional)
material_type    id, nome, slug (único)          # nota de aula, slides, artigo, guia...
license          id, nome, identificador, url_referencia, descricao_curta
material         id, slug (único), titulo, descricao, profile_id (proprietário),
                 category_id, material_type_id, license_id, estado_publicacao,
                 publicado_em, criado_em, atualizado_em
file_asset       id, material_id (1:1 no MVP), chave_interna (única), nome_original_seguro,
                 tipo_detectado, tamanho_bytes, checksum, estado_processamento, enviado_em
```

**Estados de publicação** (`material.estado_publicacao`): `RASCUNHO` (opcional no MVP),
`PUBLICADO`, `RETIRADO`. Somente `PUBLICADO` é elegível a busca, listagem e acesso a arquivo
(RN002, RN013). Se rascunho for omitido, o material nasce direto em `PUBLICADO`.

**Invariantes do §11.3 → como ficam garantidas:**

| Invariante | Onde é garantida |
|---|---|
| E-mail de login único | `UNIQUE(account.email)` |
| Slug público de perfil único | `UNIQUE(profile.slug)` |
| Identificador público de material único | `UNIQUE(material.slug)` |
| Material público exige metadados obrigatórios e arquivo válido | validação no serviço + `NOT NULL` de `titulo, descricao, category_id, material_type_id, license_id, file_asset_id` |
| Propriedade determinada no servidor | `material.profile_id` vem do contexto autenticado, nunca do payload (RN001) |
| Categorias/tipos/licenças referenciados existem | chaves estrangeiras + RN005/RN006 |
| Estado de publicação decide elegibilidade | filtro obrigatório em toda consulta pública |
| Segredos e dados privados fora da resposta pública | DTO público separado (RNF013) |

Não criar no MVP: autoria múltipla, colaboração, relações entre materiais, coleções, trilhas —
registrados como futuro (RN do §11.2 + ADR-0002).

## 3. Busca, ordenação e indexação (§12)

- **Busca lexical** sobre `titulo` e `descricao` (pode incluir nome público do autor e categoria
  se compatível). Sem embeddings, sem motor externo.
- Implementação: `tsvector` + índice GIN + `unaccent` (português: acento importa).
- **Ordenação padrão documentada:** `publicado_em DESC` com desempate estável por `id`
  (a alternativa lexical fica para quando houver algoritmo real — nada de "relevância"
  simulada).
- Filtros operam **somente** sobre materiais elegíveis, exceto na área autenticada de materiais
  próprios (RN013).
- **SEO técnico:** páginas públicas renderizadas no servidor com conteúdo real no HTML; URLs
  canônicas e estáveis; `robots.txt` e sitemap refletindo a política; rotas privadas fora da
  indexação; material retirado deixa de ser servido.

## 4. Armazenamento de arquivos (§14.4)

Interface `StorageService` (domínio não conhece provedor) com implementação local agora:

- arquivos **fora** de diretório público, servidos por endpoint que checa visibilidade e
  autorização;
- chave interna gerada pelo sistema — nunca o nome original decide o caminho;
- `checksum` e `tipo_detectado` no `file_asset`; tipo validado por conteúdo, não só extensão;
- gravação trata **falha parcial**: sem arquivo validado não existe material publicado (RN008);
- limites em configuração (§5.4): `scipath.upload.tipos-permitidos=pdf`,
  `scipath.upload.tamanho-maximo=50MB`.

## 5. Páginas e estados (§15)

Públicas: Início · Explorar materiais · Detalhe do material · Perfil público · Categoria (se
adotada como rota) · Login · Cadastro · Termos/privacidade.
Autenticadas: Área do usuário · Editar perfil · Publicar material · Materiais próprios ·
Editar material.

Toda página que depende de dados trata explicitamente **sete** estados (carregando, sucesso,
vazio, erro de rede/servidor, não encontrado, não autorizado, sessão expirada); todo formulário
trata **seis** (campo inválido, envio em andamento, falha recuperável, sucesso, tentativa
duplicada, arquivo acima do limite ou formato inválido). São **componentes reutilizáveis**, não
implementações por tela — RNF003 exige seguir `DESIGN_SYSTEM.md` e `PAGE_BLUEPRINTS.md`
(a criar). Proibido: dado mockado numa tela como se fosse real (§15.3).

## 6. Testes e qualidade (§17)

- **Unitário:** validação de campos obrigatórios; estados de publicação; autorização por
  propriedade; unicidade de e-mail e slug; categorias/licenças válidas; filtros e paginação;
  regras de acesso a arquivo.
- **Integração:** cadastro e login; criação de material com upload; **falha de storage durante a
  publicação**; persistência e leitura de metadados; listagem e busca; edição e retirada;
  tentativa de acesso cruzado entre usuários.
- **E2E:** jornadas de descoberta, cadastro, publicação e gerenciamento, em viewport móvel e
  desktop.
- **Segurança (negativos):** rota privada sem sessão; A edita material de B; tipo de arquivo não
  permitido; arquivo acima do limite; `proprietario_id` adulterado no payload; consulta a material
  removido; XSS; resposta pública sem e-mail, hash, token ou chave interna.
- **DoD (§17.5):** critérios de aceite atendidos · validação e erro implementados · testes
  proporcionais ao risco · design system e acessibilidade respeitados · sem regressão conhecida ·
  documentação atualizada · build e testes passando · relatório do §21.4 preenchido.
- Sem Docker nesta máquina: integração usa o **PostgreSQL local** com banco de teste próprio
  (`scipath_test`) — nunca H2 para testar SQL real.

## 7. Ambientes (§14.5)

Documentar e manter: desenvolvimento local; testes automatizados; build de produção; variáveis
de ambiente necessárias; configuração de banco e storage; migrações; inicialização com dados de
referência (categorias, tipos, licenças); procedimento de implantação e rollback. Segredos vêm de
configuração/ambiente e **nunca** entram no repositório (RNF012).

## 8. Decisões que ainda bloqueiam partes deste documento (§20.3)

| Decisão | Bloqueia | Onde será registrada |
|---|---|---|
| Ratificação da stack | tudo | ADR-0001 (já proposto) |
| ~~Mecanismo de autenticação e recuperação~~ **decidido:** e-mail + senha, bcrypt 12, token de recuperação de uso único | §2 (account), §5 (login/cadastro) | **ADR-0003 — registrado** |
| ~~Verificação de e-mail antes de publicar?~~ **decidido:** obrigatória para publicar; navegar e editar perfil liberados | §5, fluxo de publicação | **ADR-0003 — registrado** |
| Taxonomia inicial: categorias, tipos, licenças | §2 (seeds), §5 | ADR-0004 (Etapa 0) |
| Política de exclusão e retenção (lógica ou física) | §2 (estados), §4 (limpeza) | ADR-0005 (Etapa 0) |
| Limite de upload (confirmar 50 MB) e tipos | §4 | ADR-0005 |
| Painel administrativo mínimo vs seed/migração | §2 (gestão de categorias/licenças) | ADR-0004 |
| Hospedagem, backup e restauração | §7 | ADR-0006 (Etapa 0) |
| Quem recebe denúncia de conteúdo e como | §5 (transparência), lançamento | ADR-0006 |
