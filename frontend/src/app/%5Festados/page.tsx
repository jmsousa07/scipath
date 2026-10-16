import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Chip } from '@/components/ui/Chip';
import { DataTable, type Column } from '@/components/ui/DataTable';
import { FileField, TextAreaField, TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import { MENSAGENS_FORMULARIO, MENSAGENS_PAGINA, type FormStateKind, type PageStateKind } from '@/components/ui/mensagens';
import { PageState } from '@/components/ui/PageState';
import { Pagination } from '@/components/ui/Pagination';
import { DemoEnvio } from './DemoEnvio';

/**
 * `/_estados` — rota isolada de demonstração do card E1.7.
 *
 * Escolha registrada no card: demonstração por ROTA (e não teste de componente),
 * porque o que precisa ser verificável é o ESTADO RENDERIZADO — a associação do
 * erro ao campo, o foco visível, a mensagem acionável — e isso o QA confere na tela
 * com teclado, além do `grep` dos 13 marcadores `data-state` no HTML de produção
 * (`npm run check:states`).
 *
 * `noindex`: página de verificação, fora de qualquer navegação (§12.3 do DRS).
 * Nada aqui é dado real: todos os rótulos dizem que é demonstração de estado —
 * dado fictício apresentado como real é o risco §20.1.
 */

export const metadata: Metadata = {
  title: 'Demonstração dos estados obrigatórios',
  description:
    'Página de verificação dos 7 estados de página e 6 estados de formulário dos componentes base (DRS §15.3). Demonstração técnica, não é conteúdo do produto.',
  robots: { index: false, follow: false },
};

const ESTADOS_PAGINA: PageStateKind[] = [
  'loading',
  'success',
  'empty',
  'network-error',
  'not-found',
  'unauthorized',
  'session-expired',
];

const ESTADOS_FORMULARIO: FormStateKind[] = [
  'invalid-field',
  'submitting',
  'recoverable-failure',
  'success',
  'duplicate-attempt',
  'invalid-file',
];

type LinhaDeDemonstracao = {
  chave: string;
  titulo: string;
  tipo: ReactNode;
  estado: ReactNode;
  data: string;
};

const LINHAS_DEMONSTRACAO: LinhaDeDemonstracao[] = [
  {
    chave: 'demo-1',
    titulo: 'Linha de demonstração 1',
    tipo: <Chip tone="green">guia de estudo</Chip>,
    estado: <Badge tone="green">publicado</Badge>,
    data: 'exemplo',
  },
  {
    chave: 'demo-2',
    titulo: 'Linha de demonstração 2 (texto longo para provar que a célula quebra linha em 375 px)',
    tipo: <Chip tone="yellow">nota de aula</Chip>,
    estado: <Badge tone="yellow">retirado</Badge>,
    data: 'exemplo',
  },
  {
    chave: 'demo-3',
    titulo: 'Linha de demonstração 3',
    tipo: <Chip tone="red">artigo</Chip>,
    estado: <Badge tone="red">excluído</Badge>,
    data: 'exemplo',
  },
];

const COLUNAS_DEMONSTRACAO: Column<LinhaDeDemonstracao>[] = [
  { key: 'titulo', label: 'Título (exemplo)' },
  { key: 'tipo', label: 'Tipo de material' },
  { key: 'estado', label: 'Estado de publicação' },
  { key: 'data', label: 'Data' },
];

export default function DemonstracaoDeEstados() {
  return (
    <div className="sc-page" data-page="estados" data-estados="13">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>E1.7</i> demonstração
        </p>
        <h1 className="sc-h1">Estados obrigatórios dos componentes</h1>
        <p className="sc-sec__lead">
          Verificação, um a um, dos <strong>sete estados de página</strong> e dos{' '}
          <strong>seis estados de formulário</strong> exigidos pelo DRS §15.3 — e das primitivas que
          as telas das Etapas 2 a 5 vão reutilizar.
        </p>
      </div>

      <div className="sc-notice sc-notice--atencao mt-6" data-aviso="demonstracao">
        <Badge tone="yellow">demonstração</Badge>
        <h2 className="sc-notice__title">Isto não é conteúdo do produto</h2>
        <p className="sc-notice__msg">
          Nada nesta página é material, autoria, categoria, contagem ou data real da plataforma
          (§20.1). Os textos e as linhas de tabela são <strong>exemplos de estado</strong>: existem
          para conferir mensagem, foco, rótulo e estrutura. A página é <span className="sc-mono">noindex</span>,
          não está em nenhum menu e não é uma tela do produto.
        </p>
      </div>

      {/* ------------------------------------------------------------------ */}
      <section className="mt-10" aria-labelledby="titulo-estados-pagina">
        <h2 className="sc-subhead" id="titulo-estados-pagina">
          Estados de página <span className="sc-subhead__note">7 de 7 · DRS §15.3</span>
        </h2>
        <div className="mt-6 space-y-6">
          {ESTADOS_PAGINA.map((kind, indice) => (
            <div key={kind} className="space-y-2">
              <p className="sc-caption">
                Estado {indice + 1} de 7 — <strong>{MENSAGENS_PAGINA[kind].drs}</strong> · componente{' '}
                <span className="sc-mono">&lt;PageState kind=&quot;{kind}&quot;&gt;</span>
              </p>
              <PageState
                kind={kind}
                loginDestination={kind === 'session-expired' ? '/painel/publicar' : undefined}
                action={
                  kind === 'network-error' ? (
                    <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/_estados">
                      Tentar novamente
                    </Link>
                  ) : undefined
                }
                details={
                  kind === 'session-expired' ? (
                    <p className="sc-caption">
                      O destino pedido é preservado no login:{' '}
                      <span className="sc-mono">/entrar?proximo=%2Fpainel%2Fpublicar</span>
                    </p>
                  ) : undefined
                }
              >
                {kind === 'success' ? (
                  <Card
                    as="article"
                    eyebrow="demonstração de conteúdo"
                    title="Aqui entraria o conteúdo real da página"
                    copy="Em sucesso, <PageState> entrega o conteúdo da tela: nenhuma mensagem de estado é anunciada e a página simplesmente mostra os dados que chegaram."
                  />
                ) : undefined}
              </PageState>
            </div>
          ))}
        </div>
      </section>

      {/* ------------------------------------------------------------------ */}
      <section className="mt-12" aria-labelledby="titulo-estados-formulario">
        <h2 className="sc-subhead" id="titulo-estados-formulario">
          Estados de formulário <span className="sc-subhead__note">6 de 6 · DRS §15.3</span>
        </h2>
        <div className="mt-6 space-y-6">
          {ESTADOS_FORMULARIO.map((kind, indice) => (
            <div key={kind} className="space-y-2">
              <p className="sc-caption">
                Estado {indice + 1} de 6 — <strong>{MENSAGENS_FORMULARIO[kind].drs}</strong> · componente{' '}
                <span className="sc-mono">&lt;FormState kind=&quot;{kind}&quot;&gt;</span>
              </p>
              <FormState
                kind={kind}
                tituloNivel={3}
                fieldId={
                  kind === 'invalid-field'
                    ? 'titulo-demo-erro'
                    : kind === 'invalid-file'
                      ? 'arquivo-demo-erro'
                      : undefined
                }
                fileMotivo="tamanho"
                fileLimite="30 MB"
                fileTamanho="42 MB"
              />
            </div>
          ))}
        </div>

        <h3 className="sc-h3 mt-8">Campos que carregam esses estados</h3>
        <p className="sc-body-sm mt-2 sc-measure">
          Os dois estados de campo (<strong>inválido</strong> e <strong>arquivo não aceito</strong>)
          são associados ao CONTROLE: a mensagem sai ligada por{' '}
          <span className="sc-mono">aria-describedby</span>, o controle recebe{' '}
          <span className="sc-mono">aria-invalid</span> e o rótulo aponta para ele — os alvos das
          âncoras &quot;Ir para o campo&quot; acima são exatamente os campos abaixo.
        </p>
        <div className="mt-4 space-y-6">
          <TextField
            id="titulo-demo-erro"
            label="Título do material (exemplo de estado inválido)"
            required
            defaultValue="Uma nota de aula"
            hint="Entre 8 e 120 caracteres. É este texto que aparece na busca."
            error="O título precisa ter pelo menos 8 caracteres: descreva o material em uma frase curta e envie de novo."
          />
          <TextAreaField
            id="descricao-demo"
            label="Descrição (exemplo de campo de várias linhas)"
            hint="Explique o conteúdo, o público e o que a pessoa aprende ao usar o material."
            defaultValue="Descrição de exemplo, sem relação com material real."
          />
          <FileField
            id="arquivo-demo-erro"
            label="Arquivo do material (exemplo de arquivo recusado)"
            required
            hint="Aceitamos apenas PDF, até 30 MB (ADR-0005). O limite é validado por conteúdo no servidor."
            error="O arquivo tem 42 MB e o limite é 30 MB. Reduza o PDF (exporte com menos imagens ou divida o documento em partes) e envie novamente."
          />
        </div>

        <h3 className="sc-h3 mt-10">Envio em andamento, na prática</h3>
        <div className="mt-4">
          <DemoEnvio />
        </div>
      </section>

      {/* ------------------------------------------------------------------ */}
      <section className="mt-12" aria-labelledby="titulo-primitivas">
        <h2 className="sc-subhead" id="titulo-primitivas">
          Primitivas reutilizáveis <span className="sc-subhead__note">DESIGN_SYSTEM.md §3</span>
        </h2>

        <h3 className="sc-h3 mt-6">Botão — variantes, tamanhos, desabilitado e em andamento</h3>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <Button variant="primary">Publicar material</Button>
          <Button variant="blue">Explorar</Button>
          <Button variant="ghost">Limpar filtros</Button>
          <Button variant="text">Ver licença</Button>
          <Button size="sm">Pequeno</Button>
          <Button size="lg">Grande</Button>
          <Button disabled>Desabilitado</Button>
          <Button loading>Enviando…</Button>
        </div>

        <h3 className="sc-h3 mt-8">Card</h3>
        <div className="mt-3 flex flex-wrap gap-4">
          <Card
            as="article"
            eyebrow="exemplo"
            title="Card de material (demonstração)"
            copy="Eyebrow, título e resumo são opcionais; o card aceita qualquer conteúdo."
            className="max-w-md"
          />
          <Card hoverable pad title="Card com destaque" copy="Variante `--pad` + `--hoverable`." className="max-w-md" />
        </div>

        <h3 className="sc-h3 mt-8">Badge e chip</h3>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Badge>neutro</Badge>
          <Badge tone="blue">azul</Badge>
          <Badge tone="green">verde</Badge>
          <Badge tone="red">vermelho</Badge>
          <Badge tone="yellow">amarelo</Badge>
          <Badge tone="purple">roxo</Badge>
          <Badge tone="dark">escuro</Badge>
          <Badge tone="green" pulse>
            acontecendo agora
          </Badge>
          <Chip>categoria de exemplo</Chip>
          <Chip tone="green">licença aberta</Chip>
          <Chip tone="yellow">revisar depois</Chip>
          <Chip tone="red" dot={false}>
            sem ponto
          </Chip>
        </div>

        <h3 className="sc-h3 mt-8">Tabela com cabeçalho</h3>
        <p className="sc-body-sm mt-2 sc-measure">
          O nome da tabela vai no <span className="sc-mono">&lt;caption&gt;</span> (só para leitor de
          tela), o cabeçalho usa <span className="sc-mono">&lt;th scope=&quot;col&quot;&gt;</span> e as
          células quebram linha: a tabela cabe em 375 px sem rolagem horizontal (RNF002).
        </p>
        <div className="mt-3">
          <DataTable<LinhaDeDemonstracao>
            caption="Tabela de demonstração — conteúdo ilustrativo, não é acervo real"
            columns={COLUNAS_DEMONSTRACAO}
            rows={LINHAS_DEMONSTRACAO}
            rowKey={(linha) => linha.chave}
          />
        </div>

        <h3 className="sc-h3 mt-8">Tabela sem nenhuma linha (estado vazio do componente)</h3>
        <div className="mt-3">
          <DataTable<LinhaDeDemonstracao>
            caption="Mesma tabela com zero linhas"
            columns={COLUNAS_DEMONSTRACAO}
            rows={[]}
            rowKey={(linha) => linha.chave}
            emptyLabel="Nenhum registro para exibir."
            emptyHint="O cabeçalho permanece e a página continua explicando o próximo passo."
          />
        </div>

        <h3 className="sc-h3 mt-8">Paginação</h3>
        <p className="sc-body-sm mt-2 sc-measure">
          Links de verdade (funcionam sem JavaScript), página atual marcada com{' '}
          <span className="sc-mono">aria-current=&quot;page&quot;</span>, limites desabilitados em vez
          de sumir, e alvo de 44 px para dedo e teclado.
        </p>
        <Pagination page={2} totalPages={7} buildHref={(pagina) => `/_estados?pagina=${pagina}`} />
        <Pagination page={1} totalPages={4} buildHref={(pagina) => `/_estados?pagina=${pagina}`} label="Paginação (primeira página)" />
        <Pagination page={4} totalPages={4} buildHref={(pagina) => `/_estados?pagina=${pagina}`} label="Paginação (última página)" />
      </section>
    </div>
  );
}
