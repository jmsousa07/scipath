# SciPath — visão, escopo e critério de sucesso (MVP)

> **Fonte #1:** `assets/raw_files/drs_scipath_v1.0.pdf` — Documento de Requisitos de Software,
> versão 1.0, 16/10/2026. Este arquivo **resume** o DRS para leitura rápida no repositório;
> em qualquer divergência, o DRS vence (§21.1). Alteração aqui que mude escopo exige ADR
> registrando motivação, requisitos afetados e critérios revisados (§22).
> Ver `docs/adr/ADR-0002-drs-como-fonte-do-mvp.md` (reconciliação do escopo).

## 1. O que é

Plataforma de **descoberta e compartilhamento de conhecimento científico e acadêmico**:
pessoas encontram materiais úteis — notas de aula, slides, artigos, apresentações, guias de
estudo, recursos complementares — e compreendem rapidamente o que cada material contém,
quem o disponibilizou, a qual área pertence e sob quais condições pode ser acessado ou
reutilizado.

Dois ambientes conectados, com identidade, linguagem e componentes compartilhados:

- **Discovery / Editorial** — experiência pública, explorável e indexável, orientada à
  descoberta de materiais e autores. Prioriza leitura, exploração e contexto.
- **Application / Workspace** — experiência autenticada para criar perfil, publicar, editar
  e administrar os próprios materiais. Prioriza tarefas claras e eficientes.

**Ciclo essencial do primeiro produto:**
Explorar → encontrar → entender → acessar → criar conta → construir perfil → publicar → gerenciar.

## 2. Princípios de produto (vinculantes)

1. **Conhecimento antes do ruído** — destacar conteúdo, autoria e contexto; nada de
   mecanismos de engajamento vazios.
2. **Descoberta com contexto** — um arquivo não é um link solto; sua página explica o que é,
   quem publicou e sob quais condições.
3. **Credibilidade sem falsas garantias** — nunca insinuar revisão por pares, verificação
   institucional ou certificação que não ocorreu (RN010, RN011, CA012).
4. **Abertura com responsabilidade** — respeitar direitos autorais e as condições de uso declaradas.
5. **Publicação compreensível** — publicar não pode exigir conhecimento técnico de repositórios.
6. **Indexabilidade desde o início** — páginas públicas renderizáveis e adequadas a buscadores.
7. **Segurança por padrão** — autorização no servidor, validação de dados, controle de propriedade.
8. **Escopo deliberadamente enxuto** — cada funcionalidade sustenta descoberta, contexto,
   acesso ou publicação.

## 3. Problema e público

**Problema:** materiais acadêmicos circulam por canais dispersos (drives, páginas pessoais,
repositórios institucionais, redes sociais, grupos de mensagem). Isso dificulta encontrar
recursos, entender o contexto, identificar autoria e retornar ao material.

**Público primário:** estudantes universitários; docentes e monitores; pesquisadores; pessoas
com vínculo acadêmico ou científico. Leitura pública não exige conta; **publicar exige conta
autenticada**. O MVP não exige e-mail institucional verificado — a arquitetura de identidade
deve permitir verificação institucional ou ORCID no futuro, sem depender delas no fluxo básico.

## 4. Proposta de valor

Organizar a descoberta em torno de três elementos conectados: **material, contexto e pessoa**.
O diferencial inicial **não** é prometer revisão científica, substituir repositórios nem ser
rede social completa — é reduzir a distância entre encontrar um recurso, entender seu contexto
e acessá-lo, dando ao autor uma página pública para apresentar o que compartilha.

## 5. Objetivos do MVP

1. Descobrir e compreender materiais **sem** criar conta.
2. Criar conta, completar perfil e publicar um material.
3. Metadados coerentes para materiais, autores, categorias e licenças.
4. Proteger arquivos e operações de escrita por autenticação e autorização.
5. Páginas públicas indexáveis.
6. Base de domínio evolutiva, **sem antecipar complexidade futura**.

Indicadores iniciais (§4.2): materiais publicados, conversão do fluxo de publicação, sucesso da
busca (sessões que abrem resultado), abertura de páginas de material, acessos ao arquivo, perfis
completos, falhas de publicação. Não exige painel analítico: log operacional e instrumentação
mínima bastam. Métrica nunca é exibida como sinal de qualidade científica.

## 6. Escopo do MVP

**Incluído:** site público responsivo; página inicial com proposta de valor; listagem e
exploração de materiais; busca textual e filtros essenciais; página pública do material e página
pública do autor; cadastro, login e logout; edição de perfil; publicação, edição e retirada dos
próprios materiais; upload e acesso controlado a arquivos; categorias científicas e licenças
selecionáveis; validação de formulário, feedback e estados vazios; SEO técnico básico; controles
mínimos de segurança, observabilidade e testes automatizados.

**Fora do MVP (explícito — não entra como "melhoria óbvia"):** feed social e algoritmo de
recomendação; curtidas, comentários, seguidores, mensagens, notificações sociais; revisão por
pares, selo de qualidade científica ou avaliação formal de conteúdo; verificação institucional
obrigatória, ORCID OAuth ou federação de identidade; pagamentos, planos, monetização; apps
nativos; edição colaborativa em tempo real; OCR avançado, extração automática de metadados por
IA ou sumarização generativa; busca semântica/embeddings ou infraestrutura distribuída de busca;
repositório com versionamento científico, DOI ou preservação certificada; painéis analíticos
complexos; integrações automáticas (Drive, Zotero, arXiv, Zenodo, sistemas institucionais);
sistema complexo de denúncias, moderação em múltiplos níveis ou workflow editorial.

**Diferencial de longo prazo (registrado como futuro, não como MVP):** coleções, relações entre
materiais, trilhas de aprendizagem, reputação, comunidades temáticas e integrações acadêmicas.
A visão de longo prazo **não autoriza** implementar nada disso agora (§2.2).

## 7. Definição de "publicação" no MVP

Material publicado é o registro que passou pelas validações, está marcado como público e pode ser
acessado pela página pública. O MVP deve diferenciar publicado de não publicado **caso exista
rascunho**; para reduzir complexidade, rascunhos podem ser omitidos — e nesse caso o material só
é criado quando o fluxo de publicação conclui com sucesso.

## 8. Limites iniciais (parâmetros de produto, centralizados em configuração)

- Formato inicial: **PDF**. Outros formatos só com decisão explícita.
- Tamanho máximo: **50 MB**, configurável.
- Categorias: conjunto inicial administrável, expansível (seed/migração controlada).
- Idioma da interface: **pt-BR**. Público inicial: web responsiva.

O agente não aumenta limite nem aceita qualquer tipo de arquivo por conta própria (§5.4).

## 9. Critério de sucesso do MVP

> Uma pessoa consegue **descobrir** um material útil sem conta; **compreender** seu contexto e sua
> autoria; **acessar** o arquivo segundo as condições apresentadas; e, como publicador, **criar
> conta, construir perfil e disponibilizar um material** de forma segura e administrável.

Operacionalmente, o MVP é aceitável quando os 12 critérios globais do DRS §16 estiverem
demonstrados em ambiente de teste: CA001 descoberta pública · CA002 contexto do material ·
CA003 identidade pública · CA004 cadastro e sessão · CA005 publicação completa · CA006 validação
de arquivo · CA007 autorização · CA008 retirada de publicação · CA009 responsividade e
acessibilidade · CA010 qualidade de entrega · CA011 SEO básico · CA012 transparência.

## 10. Direção visual

**Academic Modernism** (§3.5): rigor e clareza com expressão contemporânea e acessível.
Referências de princípios — Google Research, Are.na e Wellcome (descoberta/organização
editorial); DeepMind Science e Stanford (autoridade e expressão científica); Vercel (interação e
acabamento); Digital Architect / Aura (experimentação visual). Referência **não** é template:
nada de reproduzir marcas, layouts proprietários ou a direção visual/funcional do Scirolls.

No repositório, a direção visual está materializada em:

- `assets/design_system_2.html` — **contrato visual principal** (base Google Research: lettering,
  paleta, tipografia local, componentes, movimento).
- `assets/design_system.html` — sistema anterior, referência secundária.

**Pendências de design (não decididas):**
- Tagline: o DRS usa "Conhecimento científico **em contexto**" e "Descobrir, compreender e
  compartilhar conhecimento"; o DS2 traz o lettering "Conhecimento científico, interligado".
  Revisão vira card próprio — nenhum agente altera o DS2 por conta própria.
- `DESIGN_SYSTEM.md` e `PAGE_BLUEPRINTS.md` **não existem** no repositório e são exigidos pelo
  DRS (RNF003, §21.1): precisam ser derivados do DS2 e do §15 antes das telas do MVP.
