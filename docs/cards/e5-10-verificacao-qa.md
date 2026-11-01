# E5.10 — Verificação independente da Etapa 5 (gerenciamento do material próprio)

- **Perfil:** qa · **Depende de:** E5.9 (integração no `main`) · **Status:** pronto para execução

## Contexto

Gate de verificação da Etapa 5, no `main` já integrado. Quem implementou não verifica. Esta rodada
tem uma responsabilidade a mais: a aceitação do MVP deixou **CA007 e CA008 parcialmente NÃO
VERIFICADOS** (`docs/qa/p5-aceitacao-mvp.md` §2.7, §2.8 — sem operação de edição não há acesso
cruzado a tentar; o ato de retirar não existia). É aqui que as duas metades voltam a ser medidas.

## Entrada

`docs/qa/p5-aceitacao-mvp.md` (o "antes"), `docs/adr/ADR-0011-...md`, contrato v0.6,
`PAGE_BLUEPRINTS.md` §12–§13, `docs/qa/TEMPLATE.md`, `infra/qa-harness.sh`, `Makefile`.

## Escopo da verificação

1. **RF015 — listar e ler os próprios**: a lista traz **só** o perfil da sessão (A não vê material de
   B, nem por URL direta), os três estados aparecem, vazio é 200 com `items: []`, paginação estável,
   nenhum campo privado no corpo.
2. **RF016 — editar**: edição válida persiste e **a página pública reflete**; `slug`, `publicadoEm` e
   propriedade **não** mudam; taxonomia inexistente → 422 no campo; `autorSlug`/`contaId` forjados no
   corpo são ignorados (conferência no banco).
3. **RF017/RF019 — retirada, republicação e exclusão**: estado medido **por superfície**, como no P5
   (listagem, busca, detalhe, arquivo, perfil do autor) — o ato existe e o efeito continua o medido;
   republicação volta ao público; mesmo estado é idempotente sem escrita; `EXCLUIDO` no corpo → 422;
   `DELETE` é lógico (metadados no banco), o binário continua lá **antes** dos 7 dias e a rotina de
   retenção o purga **depois** (rodada de verdade, com a contagem observada e a segunda execução sem
   efeito).
4. **§17.4 — negativos, um a um**: rota privada sem sessão; escrita sem `X-CSRF-Token`; **A tenta
   editar, retirar e excluir material de B** (medindo status **e** ausência de efeito no banco);
   consulta direta a material removido; identificadores de proprietário adulterados no payload;
   resposta pública sem e-mail/hash/token/chave interna.
5. **CA007 e CA008 — veredito novo**, explícito, dizendo o que fecha do P5 e o que continua fora.
6. **Interface**: `/painel/materiais` e `/painel/materiais/[slug]/editar` no navegador real — sete
   estados de página, seis de formulário, a **confirmação da exclusão** presente e legível (RF017),
   ausência de material fictício, teclado e foco visível, 375 px e 1440 px, `prefers-reduced-motion`,
   um screenshot por viewport.
7. **Gate do §17.5 no `main`**: `make test`, `make lint`, `make contract:check` e
   `infra/qa-harness.sh`, cada um com a saída crua (sem encadear por pipe), separando o que for
   defeito do **instrumento** do que for do produto.
8. **Declare o que NÃO foi provado** — pelo menos: anonimização dos metadados aos 90 dias
   (**pendência aberta**, ADR-0011 §9), leitor de tela real, navegador de outro motor, conferência
   visual humana, MinIO/S3, hospedagem/backup/termos/denúncia (ADR-0007 — o sistema **não** é
   declarado pronto para produção).

## Fora de escopo

Corrigir. Defeito encontrado vira card próprio, com a medição e o comando que reproduz. Editar o
laudo do P5 (é registro histórico do que foi medido em 2026-11-01): a atualização dos vereditos vem
como adendo no aceite desta etapa.

## Critérios de aceite

1. `docs/qa/aceite-etapa-5.md` + log bruto versionado, por item do escopo, com comando e saída
   colada, no formato de `docs/qa/aceite-etapa-4.md`.
2. Veredito por critério (ATENDE / NÃO ATENDE / **NÃO VERIFICADO**, com a razão de cada um) e a
   seção "o que esta verificação não prova".
3. Script de entrada reproduzível versionado (`docs/qa/e5-10-evidencia.sh`), para a rodada seguinte
   ser um comando.
4. Cards dos defeitos encontrados listados no laudo.

## Rastreabilidade

RF015–RF017, RF019; RN001, RN002, RN012, RN013; RNF004, RNF006–RNF010, RNF013, RNF021, RNF024, RNF025;
CA007, CA008, CA009, CA010; DRS §16, §17.1–§17.5, §21.3–§21.4; ADR-0003, ADR-0005, ADR-0007, ADR-0011.
