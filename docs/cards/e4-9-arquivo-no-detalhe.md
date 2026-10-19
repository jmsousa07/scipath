# E4.9 (correção) — Backend: metadado do arquivo no detalhe do material

- **Perfil:** backend · **Depende de:** E4.3 · **Status:** pronto para execução
- **Card no quadro (recriado):** substitui `t_4c5ee9d9`, que nasceu com `workspace: scratch` por
  defeito do criador — o corpo é o mesmo, o workspace é o que muda.

## Contexto

O contrato **v0.5** (E4.1) tornou `arquivo` um campo **obrigatório** do schema `MaterialDetalhe`
(`contracts/openapi.yaml`: `required: [... arquivo ...]`), com o shape `ArquivoMaterial`
(`nomeOriginal`, `tamanhoBytes`, `disponivel`) — é o que a tela usa para oferecer o download sem
inventar endereço. O código **não publica esse campo**: `MaterialDetalheResposta` tem 10 campos e
`CAMPOS_DETALHE` não seleciona nada de `file_asset`. É divergência entre contrato e código que
nenhum card da Etapa 4 declara como seu — achada na execução do E4.3, registrada sem ser corrigida
de passagem (AGENTS.md regra 6), e a bateria do E4.8 também a encontra.

## Escopo

1. `GET /api/materiais/{slug}` devolve `arquivo: { nomeOriginal, tamanhoBytes, disponivel }`:
   - `nomeOriginal` = `file_asset.nome_original_seguro` (já sanitizado no E4.2), `tamanhoBytes` =
     `file_asset.tamanho_bytes`;
   - **`disponivel` é decidido no servidor** (RF014): `true` quando o arquivo pode ser servido agora
     (`estado_processamento = 'VALIDADO'` e binário presente), `false` caso contrário — **sem**
     revelar o motivo, o caminho, a chave de armazenamento ou o checksum (RNF013, RNF014).
2. **O resto do corpo fica intacto**: nada de `id`, `file_asset_id`, `chave_interna` ou licença
   reescrita. As baterias das Etapas 2 e 3 leem o corpo atual.
3. `MateriaisDetalheIntegrationTest` afirma hoje `containsExactlyInAnyOrder(...)` com os campos do
   contrato **v0.3**; a expectativa muda **com o contrato**, de forma explícita — não afrouxe a
   asserção para "contém".

## Fora de escopo

Publicar material (E4.4), download (E4.3), listagem. Mudar o contrato (se o contrato tiver defeito,
o card volta para o PO).

## Critérios de aceite

1. Suíte: material público com `arquivo` completo e `disponivel: true`; material cujo binário não
   está servível → `disponivel: false` **e mesmo assim 200** (o detalhe continua sendo do material
   público); nenhum campo proibido no corpo.
2. `make contract:check` verde e `./mvnw -o test` verde (heap limitado), saída em
   `docs/qa/e4-9-<data>.log`.
3. Declarar no card se `MateriaisService`/`MateriaisRepositorio` (provavelmente tocados também pelo
   E4.4) divergirem na integração.

## Colisão declarada

`backend/src/main/java/com/scipath/material/MateriaisService.java` e `MateriaisRepositorio.java`
também são candidatos do E4.4 (POST /materiais). Quem integrar (E4.7) resolve e **declara** o lado
escolhido.

## Rastreabilidade

RF005, RF014, RN002; ADR-0001 (contrato primeiro); ADR-0005 §2; contrato v0.5 (`MaterialDetalhe`,
`ArquivoMaterial`).
