# E4.4 — Backend: publicar material (metadados, taxonomia, autor e estado)

- **Perfil:** backend · **Depende de:** E4.3 · **Status:** pronto para execução

## Contexto

Fecha o caminho de publicação: metadados válidos, taxonomia existente, autor da sessão e arquivo
já validado, tudo **numa transação** — é o que garante que falha não deixa material público
incompleto. Requisitos: RF012, RF018, RF019, RN008; ADR-0003 (verificação obrigatória para
publicar); ADR-0005 §2 (ordem obrigatória).

## Escopo

1. **`POST /materiais`** conforme o contrato v0.5:
   - campos mínimos do RF012 (`título`, `descrição`, `categoria`, `tipo`, `licença`, arquivo,
     `declaracaoDireito`) validados **no servidor** (a validação de cliente é conveniência, nunca
     autoridade);
   - **autor = conta da sessão**, jamais campo do cliente;
   - **exige e-mail verificado**: conta `PENDENTE_VERIFICACAO` recebe `403` com mensagem clara
     ("confirme seu e-mail para publicar") — ADR-0003;
   - categoria/tipo de material/licença precisam existir: identificador inexistente → `422`
     (RF018); nenhuma licença é atribuída automaticamente (RF018);
2. **Ordem do RN008**: receber e validar (E4.2) → mover para o storage definitivo sob a chave
   interna (E4.3) → gravar `file_asset` + `material` **na mesma transação**. Falha na transação
   marca o arquivo como órfão (removido pelo coletor, E4.5) e **nenhum** material fica público
   incompleto.
3. **Estado inicial `PUBLICADO`** ao concluir (RF019); transições válidas definidas em um só
   lugar, com estado inválido recusado (`422`) — requisição arbitrária não define estado.
4. `slug` gerado no servidor, único, estável (o material já publicado não troca de URL ao editar
   metadados na Etapa 5).
5. **Resposta `201`** com o link da página pública (RF012).

## Fora de escopo

Retirar/excluir/editar (Etapa 5). Download (E4.3). Formulário (E4.6).

## Critérios de aceite

1. Suíte cobrindo: `201` com link e estado `PUBLICADO`; `401` sem sessão; `403` com conta não
   verificada (e nenhum material criado — conferir no banco); `422` para categoria/tipo/licença
   inexistente; `400` para campo obrigatório ausente; tentativa de enviar autor pelo corpo
   **ignorada** (autor continua sendo o da sessão).
2. **Teste de rollback do RN008**: falha forçada na gravação depois do arquivo mover → nenhum
   material público existe, o arquivo fica marcado como órfão e o endpoint responde erro.
3. Teste de estado inválido por requisição arbitrária → recusado.
4. `./mvnw -o test` verde (heap limitado), saída colada em `docs/qa/e4-4-<data>.log`; lint e
   contrato verdes.

## Rastreabilidade

RF012, RF018, RF019, RN008; ADR-0003 (b), ADR-0005 §2, ADR-0006; DRS §8.3.1, §8.3.7, §8.3.8.
