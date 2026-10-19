# E4.8 — Verificação independente da Etapa 4 (publicação)

- **Perfil:** qa · **Depende de:** E4.7 (integração) · **Status:** pronto para execução

## Contexto

Gate de verificação da etapa, no `main` já integrado. Quem implementou não verifica; esta é a medida
independente dos requisitos de publicação. Mesmo método dos gates anteriores: bateria reproduzível,
laudo versionado, e seção explícita do que **não** foi verificado.

## Escopo da verificação

1. **Secretária de arquivo (RF013, RN008)**: upload real de PDF válido; arquivo renomeado `.pdf` sem
   assinatura; arquivo acima do limite; nome com traversal; MIME mentindo. Cada um com o código e a
   **mensagem** observados.
2. **Ordem do RN008**: prova de que material e arquivo só existem juntos — falha forçada no meio não
   deixa material público apontando para arquivo ausente nem arquivo servível sem material.
3. **Acesso ao arquivo (RF014, RN002/RN003)**: público baixa; retirado, excluído e inexistente
   devolvem resposta **indistinguível**; a resposta não carrega caminho interno nem segredo
   (inspecionar corpo e cabeçalhos).
4. **Publicação (RF012, RF018, RF019)**: campos mínimos validados no servidor; autor vem da sessão
   (tentar forçar autor no corpo e observar); conta sem e-mail verificado recebe `403` e **não cria**
   material; categoria/tipo/licença inexistente recusados; estado inicial correto.
5. **Coerência entre superfícies**: material publicado aparece na listagem, na busca e no detalhe;
   depois de manipulado o estado, some das três — inclusive por URL direta (CA008, RF019).
6. **Interface no navegador** (`/painel/publicar`, `/painel/materiais` se existir): campos e listas
   presentes no HTML entregue, mensagem específica de erro visível, teclado e foco, contraste,
   375 px e 1440 px, `prefers-reduced-motion` (RNF007, RNF008, CA009) — os achados do gate da
   Etapa 2 mostram que essa camada **não** é coberta por leitura de HTML.
7. **Integridade e limpeza**: coletor de órfãos rodado de verdade, com a contagem observada; segunda
   execução sem efeito.
8. **Gate do §17.5 no `main`**: `make test`, `make lint`, `make contract:check`, com saída crua; e o
   harness do projeto (`infra/qa-harness.sh`), declarando os NÃO ATENDE que forem defeito do
   instrumento (como no E3.8), separados dos do produto.

## Entregável

- Script de entrada reproduzível (`bash docs/qa/e4-8-evidencia.sh`) e laudo
  `docs/qa/e4-publicacao-<data>.log`, com veredito por item do escopo, números medidos e a seção
  **"O QUE ESTA VERIFICAÇÃO NÃO PROVA"** (leitor de tela, conferência visual humana, navegador de
  outro motor, envio de e-mail real).
- Achado que for defeito real: card próprio, com a medição e o comando que reproduz.

## Rastreabilidade

RF012–RF019 (os que pertencem a esta etapa), RN002, RN003, RN008, RN012, RNF004, RNF007, RNF008,
RNF014, RNF023, CA008, CA009; DRS §17.3, §17.5, §21.4; ADR-0003, ADR-0005.
