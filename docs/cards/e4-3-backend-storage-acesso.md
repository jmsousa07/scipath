# E4.3 — Backend: storage definitivo e acesso ao arquivo público

- **Perfil:** backend · **Depende de:** E4.2 · **Status:** pronto para execução

## Contexto

Com o arquivo validado (E4.2), falta onde ele mora e como o visitante o obtém — sem nunca expor
infraestrutura. Requisitos: RF014, RN002, RN003, RNF014; ADR-0005 §2 (interface `StorageService`,
chave UUID, arquivos fora de diretório público).

## Escopo

1. **`StorageService`** no domínio (mesmo padrão do `EmailSender` do E3.4) com implementação
   **local** agora: guardar/mover/remover por chave interna, sem que o domínio conheça o disco.
   Caminho base em configuração; diretório **fora** de qualquer raiz servida.
2. **Chave interna UUID** decide o caminho; o nome original nunca participa dele.
3. **`GET /materiais/{slug}/arquivo`** (contrato v0.5):
   - resolve o material pelo slug, decide visibilidade **no servidor** a partir do estado
     persistido (RF019) e das regras de autorização (RN002/RN003);
   - público → `200` com `application/pdf`, `Content-Disposition: attachment` e o nome original
     sanitizado; privado/retirado/excluído/inexistente → **a mesma resposta de não encontrado**,
     indistinguível;
   - não revela caminho interno, chave de storage, nem detalhe de infraestrutura (RF014); erro
     consistente, sem stack.
4. **Remoção de arquivo** pelo serviço, usada depois pelo coletor (E4.5) e pela Etapa 5: nunca
   apaga arquivo referenciado por material vivo (ADR-0005 §3).
5. Se o checksum no disco não confere com o registrado no momento do download, **não servir o
   arquivo**: responder erro e registrar o incidente (integridade, RN008).

## Fora de escopo

Gravar material/publicar (E4.4). Coletor de órfãos (E4.5). Retirar/excluir pelo autor (Etapa 5).
Trocar o backend de storage por MinIO/S3 (ADR-0005 §2: só a implementação muda, o domínio não).

## Critérios de aceite

1. Suíte cobrindo: download de material público `200` com o cabeçalho correto; material retirado,
   excluído e inexistente devolvendo resposta **idêntica** (comparar status e corpo, não só o
   status); resposta sem caminho/segredo no corpo nem nos cabeçalhos.
2. Teste que prova que o diretório de storage **não** está sob `frontend/public`, `resources/static`
   ou qualquer raiz servida.
3. Teste de checksum divergente: o arquivo não é servido.
4. `./mvnw -o test` verde (heap limitado), saída colada em `docs/qa/e4-3-<data>.log`; lint e
   contrato verdes.

## Rastreabilidade

RF014, RF019, RN002, RN003, RNF014; ADR-0005 §2, §3; DRS §14.4, §11.1.4.
