# E4.2 — Backend: receber e validar o arquivo enviado

- **Perfil:** backend · **Depende de:** integração do contrato v0.5 · **Status:** pronto para execução

## Contexto

Primeira metade do caminho de publicação: transformar um upload em um arquivo comprovadamente
aceitável, sem deixá-lo disponível antes disso (RF013, RN008, ADR-0005 §1). O contrato v0.5 já
descreve o `POST /materiais` com arquivo; aqui se implementa a **recepção e validação** — a
gravação do material é do E4.4.

## Escopo

1. **Limites em configuração, num só lugar** (ADR-0005 §1, §5.4): `SCIPATH_UPLOAD_MAX_BYTES`
   (30 MB por padrão) e os dois limites do Spring multipart, derivados do mesmo valor. Valores em
   `.env`, nunca constantes espalhadas.
2. **Validação por conteúdo**: aceitar apenas PDF de verdade — verificar extensão, MIME declarado
   **e assinatura** `%PDF-` nos primeiros bytes (RF013). Extensão nunca decide sozinha.
3. **Tamanho aplicado no servidor**, não só no cliente (RF013), com resposta `413` e mensagem
   específica: "arquivo acima de X MB" — nunca 500 genérico (RNF004).
4. **Nome físico gerado pelo sistema** (UUID); o nome original é preservado apenas como metadado
   **sanitizado** (`/`, `\`, `..` neutralizados). Nenhum caminho derivado da entrada do usuário —
   sem traversal e sem execução do conteúdo enviado (RF013).
5. **Área temporária** para o recebimento, fora de qualquer diretório servido: o arquivo **não é
   disponibilizado** antes da validação completa (RF013, RN008).
6. **Checksum SHA-256** calculado no recebimento e guardado em `file_asset` junto de chave, nome
   original seguro, tipo detectado e tamanho (ADR-0005 §2).
7. Erros com `ProblemDetail`: `415` formato não aceito (PDF renomeado, MIME mentindo), `413`
   acima do limite, `400` campos ausentes.

## Fora de escopo

Mover para o storage definitivo, gravar material, responder com o link público (E4.4). Download
(E4.3). Formulário (E4.6).

## Critérios de aceite

1. Suíte cobrindo, no mínimo: PDF válido aceito; arquivo renomeado `.pdf` sem assinatura recusado
   (`415`); acima do limite recusado (`413`) **com a mensagem específica**; nome original com
   `../` neutralizado; MIME declarado divergente do conteúdo recusado.
2. Teste provando que o arquivo recebido fica em área temporária e **não** é acessível por nenhum
   endpoint antes da validação.
3. Nenhum valor de limite literal no código: teste que muda a configuração e observa o efeito.
4. `./mvnw -o test` verde com heap limitado (`MAVEN_OPTS=-Xmx384m`), saída colada em
   `docs/qa/e4-2-<data>.log`; `make lint` e `make contract:check` verdes.

## Rastreabilidade

RF013, RN008, RNF004, RNF011, RNF014; ADR-0005 §1, §2; DRS §5.4, §14.4.
