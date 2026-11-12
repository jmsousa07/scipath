# t_c10afd4a — Preparar SciPath para portfólio público

Contexto: Preparar o repositório SciPath para avaliação pública como portfólio, sem publicar, fazer push ou anunciar prontidão de produção. Fonte funcional: DRS v1.0; `main` contém o MVP E1–E5 e o QA E5.10. O histórico tem 286 commits; `.env` está ignorado e não rastreado. Nunca abrir/imprimir `.env` nem expor valores sensíveis em logs.

Entrada: `AGENTS.md`, DRS, `docs/00-visao.md`, `docs/01-arquitetura.md`, ADRs, `contracts/openapi.yaml`, `Makefile`, `.env.example`, `infra/README.md`, `docs/como-rodar-local.md`, `docs/rodar-em-outra-maquina.md`, `frontend/README.md`, fontes e histórico Git.

Saída: README raiz em pt-BR, licença MIT apenas para material original do projeto, avisos/licenças de terceiros preservados, dados pessoais removidos dos arquivos atuais, documentação pública coerente, relatório de auditoria e verificações. Capturas externas de sites foram retiradas da árvore de trabalho; não reescrever histórico nem alterar remoto sem decisão explícita do dono.

## Critérios de aceite

1. Auditar o histórico e a árvore rastreada sem imprimir valores; confirmar `.env` fora do histórico e `.gitignore`, domínios de teste reservados, ausência do caminho pessoal indicado e configuração de e-mail sem caminho local fixo. Qualquer segredo real deve ser classificado, rotacionado e removido conforme autorização do dono, sem exposição do valor.
2. README descreve propósito/público, funcionalidades comprováveis, limites, setup do zero, testes, diagrama Mermaid, ADRs/OpenAPI e uso explícito de agentes de IA.
3. Licença MIT identifica titular/ano e delimita claramente conteúdo original; não relicenciar fontes ou ativos de terceiros. Incluir licenças de fontes quando comprovadas.
4. Corrigir documentação pública obviamente desatualizada, sem refatoração oportunista nem dados fictícios apresentados como reais.
5. Verificar `make test`, `make lint`, build frontend e `make contract:check`; guardar evidência redigida sem segredos. Não fazer commit, push, reset destrutivo ou rewrite de histórico sem autorização específica.
