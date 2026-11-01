# E5.7 — Frontend: página "Editar material"

- **Perfil:** frontend · **Depende de:** E5.6 · **Status:** pronto para execução

## Contexto

Fecha a segunda metade da Etapa 5 na interface: o autor atualiza os metadados de um material próprio
(RF016; `PAGE_BLUEPRINTS.md` §13). O critério que amarra as duas pontas é explícito: *"a página
pública reflete a alteração após sucesso"*. A propriedade é decidida no servidor — a tela não é
controle de autorização (RNF010).

## Entrada

`PAGE_BLUEPRINTS.md` §12–§13 e a tabela de rotas (corrigida no E5.6), `DESIGN_SYSTEM.md`, contrato
v0.6 (`PUT /perfil/atual/materiais/{slug}`, `EdicaoDeMaterial`), `docs/adr/ADR-0011-...md` §4, E5.6
(cliente da API — **já traz as cinco funções**; não reabra `lib/api/client.ts`/`tipos.ts` sem
necessidade), `frontend/src/components/painel/FormularioPublicacao.tsx` (o formulário irmão),
`frontend/src/components/ui/`.

## Escopo

1. **Rota `/painel/materiais/[slug]/editar`** (página privada, `noindex`), com sessão obrigatória:
   sem sessão → estado "sessão necessária" e login preservando o destino.
2. **Formulário pré-preenchido** com o material lido de `GET /perfil/atual/materiais/{slug}` (o
   detalhe do dono lê `RETIRADO` também; o detalhe público responde 404 para ele). Campos: título,
   descrição, categoria, tipo de material, licença — **as três listas vêm da API** (`/categorias`,
   `/tipos-material`, `/licencas`), nunca codificadas na tela (RF018), com a escolha atual marcada
   pelo `slug`.
3. **Campos não editáveis ficam visíveis e explicados**, não escondidos: o endereço público (slug) é
   estável e não muda (RNF025) e o arquivo é o da publicação — trocar arquivo não é deste fluxo.
   Diga isso na tela em vez de deixar a pessoa procurar.
4. **Seis estados de formulário** (§15.3): campo inválido com mensagem associada ao controle, envio
   em andamento (sem envio duplo), falha recuperável com os dados preservados, sucesso, tentativa
   duplicada, e o estado de arquivo não se aplica aqui (não há upload).
5. **Erros do servidor com a mensagem específica** (RNF004): 422 de campo mapeado para o campo
   (`errors[]`), 404 (material de outra conta **ou** excluído — a tela trata como "não encontrado",
   como o servidor), 403 de CSRF, 401 de sessão expirada.
6. **Sucesso**: confirmação explícita + link para a página pública (`/materiais/{slug}`) — é onde o
   critério do RF016 é verificado.
7. **Ligação com a lista**: a ação "editar" de `/painel/materiais` (E5.6) leva a esta rota.

## Fora de escopo

Retirar/republicar/excluir (as ações ficam na lista, E5.6). Trocar o arquivo do material. Backend.
Refatorar o formulário de publicação.

## Critérios de aceite

1. `npm run build`, `npm run lint` e `npm run typecheck` verdes, saída colada.
2. Bateria do HTML entregue: com a API simulada, o formulário chega **preenchido** com o material
   existente e as listas da API aparecem; sem material fictício (AGENTS regra 9); o 422 de um campo
   aparece associado ao controle certo.
3. Navegador real: jornada por teclado (foco visível, ordem de tabulação), 375 px e 1440 px, e um
   caso de erro exibido — **um** screenshot por viewport em `docs/qa/`.
4. Evidência `docs/qa/e5-7-<data>.log` commitada com os comandos, a bateria e as medições.

## Rastreabilidade

RF016, RF018; RNF003, RNF004, RNF006–RNF008, RNF010, RNF024, RNF025; CA007, CA009; DRS §15.2, §15.3;
ADR-0011 §4.
