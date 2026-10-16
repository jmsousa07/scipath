# ADR-0005 — Upload: limites, armazenamento, exclusão e retenção

- Data: 2026-10-16
- Status: **aceita** — limite de 30 MB e política de exclusão/retenção (opção (b)) confirmados
  pelo dono em 2026-10-16
- Decisores: João Mateus (dono) · orquestração
- Resolve: DRS §20.3 itens 6 (política de exclusão) e 7 (limite de upload)
- Relaciona: DRS §5.4, §11.1.4, §14.4, RN008, RN012, CA006, CA008, §13.2

## Contexto

O DRS deixa dois parâmetros em aberto: limite de upload ("confirmar o limite inicial de 50 MB ou
definir outro valor") e política de exclusão ("exclusão lógica ou física, retenção e limpeza de
arquivos"). Ambos precisam estar fixados **e centralizados em configuração** (§5.4) antes da
Etapa 4 (publicação).

## 1. Limite de upload — decisão do dono: **30 MB**

O dono definiu 30 MB em vez dos 50 MB sugeridos no DRS. Registro minha posição, porque o risco
não é o que parece:

- 30 MB é defensável para o MVP: reduz custo de armazenamento, tempo de upload e superfície de
  abuso, e a maioria das notas de aula em PDF fica bem abaixo disso.
- **Mas o risco real é o inverso de "demais": é rejeitar material legítimo.** Apresentações
  exportadas com muitas imagens e artigos digitalizados passam de 30 MB com frequência — e o
  público-alvo do DRS (§3.2) publica exatamente slides e artigos.
- Por isso o limite **não é uma constante**: é configuração (`SCIPATH_UPLOAD_MAX_BYTES`), e
  trocar exige uma linha de `.env`, não um card. Isso é literalmente o que o §5.4 pede.

**Formatos:** apenas PDF, validado por **conteúdo** (magic bytes `%PDF-`) além do tipo MIME — a
extensão nunca decide sozinha (§5.4: "não aceitar qualquer tipo de arquivo").

**Os três lugares que precisam concordar** (senão o erro aparece longe da causa):
`SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` e o limite
de corpo do proxy reverso (quando existir). A mensagem de erro para o usuário é específica:
"arquivo acima de X MB" ou "formato não aceito", nunca um 500 genérico (RNF004).

## 2. Armazenamento

Interface `StorageService` no domínio, implementação local agora (§14.4):

- arquivos **fora** de diretório público, entregues por endpoint que verifica visibilidade e
  autorização (RN002, RN003);
- **chave interna** gerada pelo sistema (UUID); o nome original nunca determina o caminho — é
  guardado apenas como metadado sanitizado;
- `file_asset` registra chave, nome original seguro, tipo detectado, tamanho e **checksum
  SHA-256**;
- segredos de storage só em configuração (§14.4).

**Integridade da publicação (RN008) — ordem obrigatória:** 1) receber em área temporária e
validar; 2) mover para o storage definitivo sob a chave interna; 3) gravar metadados e material
na mesma transação. Se a transação falhar, o arquivo já gravado é marcado como órfão e removido
pelo coletor — **nunca** existe material publicado apontando para arquivo não validado.

## 3. Exclusão e retenção (RN012) — recomendação

Duas operações distintas, que o DRS trata separadamente (RF017, CA008):

- **RETIRAR de publicação** — reversível por natureza: o material sai da busca e do acesso
  público, o arquivo deixa de ser servido publicamente, mas o dono pode republicar. Sem prazo.
- **EXCLUIR material** — aqui entra a política:

| Opção | Como é | Prós | Contras |
|---|---|---|---|
| (a) Física imediata | metadados e arquivo apagados na hora | simples, sem custo | irreversível: engano do usuário é perda definitiva; atrapalha resposta a contestação (§13.2 permite retirar **enquanto** a contestação é avaliada) |
| (b) **Lógica com retenção curta + purga** | material vira `EXCLUIDO` (invisível, fora da busca, sem acesso público); metadados retidos para auditoria; **arquivo físico purgado após 7 dias** por rotina observável | reversível na janela; permite investigar denúncia; sem lixo permanente | exige rotina de limpeza e um estado a mais no modelo |
| (c) Lógica permanente | nunca apaga o arquivo | máximo conservadorismo | armazenamento cresce; mantém conteúdo contestado indefinidamente — o oposto do que o RN012 pede |

**Decisão do dono: (b) — ratificada em 2026-10-16.** Concretamente:

- `material.estado_publicacao` ganha `EXCLUIDO` (além de `PUBLICADO` e `RETIRADO`);
- exclusão é lógica na hora; o arquivo é purgado **7 dias** depois;
- metadados permanecem **90 dias** (auditoria e resposta a denúncia), depois anonimizados;
- o **coletor de limpeza** roda diariamente, é idempotente, **nunca** apaga arquivo referenciado
  por material vivo, registra contagem do que removeu (RN012: "operações de limpeza devem ser
  seguras e observáveis") e é testado — inclusive o caso de falha no meio;
- nenhuma cópia temporária sobrevive à operação (RN012).

Item **ratificado** pelo dono em 2026-10-16: exclusão lógica imediata, purga do arquivo após
7 dias, metadados retidos por 90 dias, coletor de limpeza diário e observável. As opções (a) e
(c) ficam registradas como alternativas descartadas.

## Consequências

- **Positivas:** limites em configuração e num só lugar; nenhum arquivo órfão indefinido;
  remoção com janela de segurança e rastro de auditoria.
- **Negativas:** mais um estado no modelo e uma rotina agendada desde cedo — custo aceito por
  ser exigência explícita do DRS (RN012), não antecipação.

## Alternativas descartadas

- **Guardar arquivo em diretório público estático.** Violaria §14.4 e RNF014 (acesso precisa
  passar por visibilidade e autorização).
- **Confiar na extensão do arquivo.** PDF renomeado de `.exe` passaria (RNF011 — abuso de upload).
- **Apagar tudo na hora (opção a).** Descartada como recomendação pelo prejuízo ao usuário e à
  apuração de denúncia; permanece registrada para o caso de o dono priorizar simplicidade.

## Rastreabilidade

- **Requisitos afetados:** §5.4, §11.1.4, §14.4, RN002, RN003, RN008, RN012, RNF004, RNF011,
  RNF014, CA006, CA008.
- **Impactos:** `docs/01-arquitetura.md` §2 (estado `EXCLUIDO`) e §4; migração V1;
  `contracts/openapi.yaml` (endpoint de arquivo e de exclusão); cards das Etapas 4 e 5.
