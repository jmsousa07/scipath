# E4.6 — Frontend: formulário de publicar material

- **Perfil:** frontend · **Depende de:** integração do contrato v0.5 · **Status:** pronto para execução

## Contexto

A página que transforma a identidade da Etapa 3 em publicação. Rota definida:
**`/painel/publicar`** (`PAGE_BLUEPRINTS.md` §11), atrás de sessão (ADR-0003: publicar exige e-mail
verificado). Requisitos: RF012, RF018, RNF004; contrato v0.5.

## Escopo

1. Formulário com os campos mínimos do RF012: título, descrição, categoria, tipo de material,
   licença, arquivo e **confirmação de que possui direito de compartilhar** — esta última visível e
   explícita, não escondida.
2. **Categoria, tipo e licença vêm da API** (`/categorias`, `/tipos-material`, `/licencas`) — nunca
   lista codificada na tela (RF018: "não codificados de forma divergente em várias telas").
3. **A licença é explicada em linguagem simples** (RF018): o que a escolha permite de reutilização.
   Nenhuma licença pré-selecionada sem escolha explícita do autor.
4. **Validação no cliente** (conveniência) refletindo as regras do servidor — o servidor continua
   sendo a autoridade.
5. **Erros do servidor com a mensagem específica** (RNF004): arquivo acima do limite, formato não
   aceito, campo inválido, categoria inexistente — sem "erro inesperado" quando a causa é conhecida.
6. **Sucesso** → link para a página pública do material (RF012), com o estado publicado visível.
7. **Conta sem e-mail verificado** (`403`): estado claro de "confirme seu e-mail para publicar", com
   o caminho para reenviar a confirmação (o reenvio existe desde o E3.4) — não um erro cru.
8. **Sem dado fictício em tela** (AGENTS.md regra 9): nenhum material de exemplo, nenhuma lista
   hard-coded.
9. Estados de carregando/enviando/erro/vazio previstos em `PAGE_BLUEPRINTS.md` §11 e no DS2.

## Fora de escopo

Listar/editar/retirar materiais próprios (Etapa 5). Mexer no cabeçalho, no painel (§9) ou no
formulário de perfil (§10). Implementar backend.

## Critérios de aceite

1. `npm run build` + `npm run lint` + `npm run typecheck` verdes, saída colada.
2. Bateria de HTML entregue (sem JavaScript): os campos mínimos, o rótulo do arquivo, a confirmação
   de direito e as listas vindas da API aparecem no documento servido — sem depender de hidratação
   (RNF023).
3. Uma prova executada de erro específico (ex.: arquivo não-PDF) mostrando a mensagem do servidor na
   tela, colada na evidência.
4. Evidência versionada em `docs/qa/e4-6-<data>.log` (log do build e da bateria; navegador é
   ferramenta do card de QA).

## Rastreabilidade

RF012, RF018, RNF004, RNF023; ADR-0003 (b); `PAGE_BLUEPRINTS.md` §11; contrato v0.5.
