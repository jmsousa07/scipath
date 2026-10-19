# E4.1 — Contrato v0.5: publicar material e acessar o arquivo

- **Perfil:** po · **Depende de:** E3.9 (fechamento da Etapa 3) · **Status:** pronto para execução

## Contexto

O contrato está em **v0.4.0** e cobre descoberta e identidade. A Etapa 4 (publicação) precisa de
duas fronteiras novas, e por regra do projeto a mudança começa aqui: `contracts/openapi.yaml`
antes do código. Requisitos: RF012, RF013, RF014, RF019; ADR-0005 (limites, storage, RN008);
ADR-0003 (verificação obrigatória para publicar); ADR-0006 (taxonomia por migração de seed).

## Escopo

1. **`POST /materiais`** — publicar material próprio. Requisição `multipart/form-data` com os
   metadados e o arquivo na MESMA requisição (é o que torna a ordem do RN008 implementável: o
   servidor recebe, valida e só então grava material e arquivo numa transação).
   - Campos: `titulo`, `descricao`, `categoriaId`, `tipoMaterialId`, `licencaId`, `arquivo`,
     `declaracaoDireito` (confirmação de que possui direito de compartilhar — RF012).
   - **O autor NÃO é campo**: vem da sessão (RF012: "definido pelo usuário autenticado no servidor,
     nunca por um campo livre enviado pelo cliente").
   - Resposta `201` com `slug`, `estadoPublicacao` e a URL da página pública (RF012).
   - Erros com `ProblemDetail` e mensagem específica (RNF004): `400` campos inválidos; `401` sem
     sessão; `403` e-mail não verificado (ADR-0003); `413` acima do limite; `415` formato não
     aceito; `422` categoria/tipo/licença inexistente (RF018).
2. **`GET /materiais/{slug}/arquivo`** — baixar o arquivo de material público (RF014).
   - `200` com `application/pdf` e `Content-Disposition: attachment; filename="<nome seguro>"`.
   - Material privado, retirado, excluído ou inexistente: **mesma resposta** (`404` equivalente),
     indistinguível (RN002, RN003) — a autorização é decidida no servidor.
   - O contrato NÃO expõe caminho, chave de storage nem credencial (RF014).
3. **`GET /materiais/{slug}`**: acrescentar o campo que diz se o arquivo está disponível, sem
   quebrar consumidores existentes (`docs/qa` e as baterias das etapas 2/3 usam o corpo atual).
4. Subir `info.version` para **0.5.0** e registrar no `CHANGELOG` do contrato, se existir.

## Fora de escopo

Listar/editar/retirar materiais próprios (RF015–RF017) — **Etapa 5**. Implementar o backend.

## Critérios de aceite

1. `make contract:check` exit 0 com a saída colada.
2. Os dois caminhos documentam todos os códigos de erro listados acima, com exemplo de corpo
   `ProblemDetail`.
3. `declaracaoDireito` é obrigatório no schema e o autor não aparece como campo de entrada.
4. Diff do contrato anexado à evidência (`docs/qa/e4-1-<data>.log`), mostrando só adição e a
   subida de versão.

## Rastreabilidade

RF012, RF013, RF014, RF018, RF019, RN002, RN003, RN008, RNF004; ADR-0003 (a)(b), ADR-0005 §1, §2;
DRS §8.3, §11.1.4, §14.4.
