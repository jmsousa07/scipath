# ADR-0013 — Licença do repositório passa de MIT para Apache-2.0

- **Status:** aceita
- **Data:** 2026-11-12
- **Substitui:** a licença MIT escolhida no card `t_c10afd4a`

## Decisão

O código original e o contrato de API (`backend/`, `frontend/` sem as fontes de terceiros,
`infra/`, `contracts/`) passam a ser licenciados sob **Apache License 2.0** (`Apache-2.0`).
`docs/`, o DRS e os ativos de terceiros continuam fora do escopo; as fontes seguem sob SIL OFL 1.1.

## Por quê

Apache-2.0 acrescenta, sobre a MIT, a concessão explícita de patente e a cláusula de retaliação —
o padrão esperado em projeto de plataforma com dependências corporativas (Spring Boot, Next.js).
O repositório é peça de portfólio e candidatura: a licença permissiva com patente explícita reduz
dúvida de quem avalia, sem restringir uso.

## Consequências

- `LICENSE` reescrito com o texto oficial da Apache-2.0 (baixado de `apache.org`,
  `[yyyy] [name of copyright owner]` preenchido) e um complemento de escopo.
- `README.md` e o aceite do portfólio atualizados; nenhuma menção a MIT permanece fora do
  `package-lock.json` (licenças de dependências) e dos registros históricos em `docs/cards/`.
- Sem mudança de código: nada além de texto de licença e menções.
