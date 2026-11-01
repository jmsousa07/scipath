import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { CardMaterialProprio } from '@/components/painel/CardMaterialProprio';
import { PageState } from '@/components/ui/PageState';
import { Pagination } from '@/components/ui/Pagination';
import { Recarregar } from '@/components/ui/Recarregar';
import { listarMateriaisProprios, type Falha } from '@/lib/api/client';
import { totalDePaginas } from '@/lib/explorar/consulta';
import { lerPagina, urlComPagina } from '@/lib/pagina';
import { rotaPreservada } from '@/lib/rotas';
import { cabecalhoDeCookies, lerSessaoNoServidor } from '@/lib/sessao/servidor';

/**
 * `/painel/materiais` — materiais próprios, com o estado de publicação e as ações do dono
 * (RF015, RF017; PAGE_BLUEPRINTS.md §12; DRS §15.2). A rota era prometida desde a Etapa 1 e
 * **não existia**: a área do usuário dizia que a listagem "ainda não existe nesta versão".
 * A partir daqui existe, e a área do usuário aponta para ela.
 *
 * Renderizada no SERVIDOR, com a sessão DESTA requisição (`lerSessaoNoServidor`) e com a
 * cookie `HttpOnly` reenviada à API (`cabecalhoDeCookies` → `listarMateriaisProprios`). Não
 * é escolha de desempenho: sem isso o HTML entregue teria um "carregando" no lugar da lista,
 * e quem não executa JavaScript — ou o rastreador de um link — não veria os próprios
 * materiais. Como a leitura usa `cookies()`, a rota é dinâmica por construção: nenhuma
 * página privada pode ser servida do cache para outra pessoa.
 *
 * Os sete estados do §15.3, e onde cada um aparece:
 *
 * | Estado | O que o provoca |
 * |---|---|
 * | `carregando` | não é documento entregue: a lista é lida no servidor antes da renderização, então o HTML chega pronto (RNF023). Não há tela intermediária que só o JS revele |
 * | `sucesso` | 200 com materiais — os três estados de publicação na mesma lista (ADR-0011 §2) |
 * | `vazio` | 200 com `items: []`: quem ainda não publicou. O estado **orienta o primeiro passo** (RF015) — publicar, ou confirmar o e-mail quando é isso que falta |
 * | `erro de rede/servidor` | a leitura não respondeu, ou respondeu fora do contrato (não há corpo json), ou 5xx. Traz "tentar de novo" |
 * | `não encontrado` | `?page=` fora do formato (o endereço não forma uma listagem) ou 404 da API. Nunca "conserto" o parâmetro em silêncio: mostrar a primeira página a quem pediu outra coisa esconderia o defeito |
 * | `não autorizado` | 403 da API: a superfície é da própria conta, e o 403 aqui significa que a plataforma recusou o acesso |
 * | `sessão expirada` | sem sessão na requisição, ou 401 entre a leitura da sessão e a da lista — login preservando `/painel/materiais` |
 *
 * O que a tela NÃO faz, de propósito: decidir o que a conta pode fazer. A derivação das
 * ações a partir do estado é de APRESENTAÇÃO (`lib/materiais/acoes.ts`) e o servidor é a
 * autoridade (RNF010): esconder o botão nunca protegeu nada. E não guarda cópia própria da
 * lista — depois de cada ação o `router.refresh()` relê o servidor, então o que está na tela
 * é o estado persistido, não o que o cliente achou que aconteceu.
 *
 * Privacidade e SEO: página privada com `noindex, nofollow` (RNF024, CA011), e o dado
 * privado não chega ao documento porque não existe nas respostas que esta tela consome —
 * e-mail de login, hash de senha, token, identificador de conta e caminho de armazenamento
 * ficam fora de `MaterialProprio` (RNF013, RNF014). A bateria de HTML serve esses campos a
 * mais no duplo de teste e exige que nenhum apareça no documento.
 */

export const metadata: Metadata = {
  title: 'Materiais próprios',
  description:
    'Os seus materiais no SciPath, com o estado de publicação de cada um e as ações de editar, retirar, repor no público e excluir.',
  // Área privada fora de índice (RNF024, CA011).
  robots: { index: false, follow: false },
};

type Props = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function MateriaisPropriosPage({ searchParams }: Props) {
  const leitura = await lerSessaoNoServidor();

  if (leitura.tipo === 'falha') {
    return (
      <Moldura>
        <PageState
          kind="network-error"
          title="Não foi possível abrir os seus materiais agora"
          message="O servidor não respondeu à consulta de sessão, então não sabemos de quem é esta lista — e mostrar o acervo de uma conta que não foi confirmada seria pior que não mostrar. Tente de novo; se continuar, tente em alguns minutos."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
              Voltar à área do usuário
            </Link>
          }
        />
      </Moldura>
    );
  }

  if (leitura.tipo === 'anonimo') {
    return (
      <Moldura>
        <PageState
          kind="session-expired"
          title="Entre para ver os seus materiais"
          message="Esta lista mostra os materiais da sua conta, com as ações de editar, retirar, repor e excluir — então ela depende de quem está pedindo. Entre com e-mail e senha: você volta para cá depois do login. Se a sua sessão estava aberta, ela expirou por inatividade — a lista é a mesma."
          loginDestination="/painel/materiais"
          details={
            <p className="sc-helper">
              Ainda não tem conta?{' '}
              <Link href="/cadastro">Criar conta</Link> leva menos de um minuto e libera navegar e
              editar o perfil na hora. Publicar exige o e-mail confirmado — a área do usuário
              explica esse passo.
            </p>
          }
        />
      </Moldura>
    );
  }

  const { estadoConta } = leitura.sessao;
  const contaAtiva = estadoConta === 'ATIVO';

  const brutos = await searchParams;
  const leituraDaPagina = lerPagina(brutos.page);

  // Parâmetro fora do formato é RECUSADO, não corrigido (mesma decisão de `lib/pagina.ts` e
  // do teto de `q` no ADR-0008): descartar o valor mostraria a primeira página para quem
  // pediu outra coisa sem que a pessoa soubesse.
  if (!leituraDaPagina.ok) {
    return (
      <Moldura>
        <PageState
          kind="not-found"
          title="Este endereço não forma uma listagem"
          message="O número da página que veio no endereço está fora do formato que a listagem aceita. Ajuste o endereço ou volte para a primeira página — os seus materiais não mudaram nada por causa disso."
          details={
            <p className="sc-body">
              Página pedida: <span className="sc-mono">{leituraDaPagina.valor}</span> — esperado:{' '}
              {leituraDaPagina.esperado}.
            </p>
          }
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
              Ver a primeira página
            </Link>
          }
        />
      </Moldura>
    );
  }

  // A leitura do dono exige a cookie de sessão, e quem a tem em mãos é o servidor desta
  // requisição (`cabecalhoDeCookies`). `page` é 0-based no contrato e 1-based na URL; a
  // conversão acontece AQUI, uma vez (a mesma regra de `/explorar`, `lib/pagina.ts`).
  const resultado = await listarMateriaisProprios(
    await cabecalhoDeCookies(),
    leituraDaPagina.pagina - 1,
  );

  if (!resultado.ok) {
    return (
      <Moldura>
        <EstadoDaFalha falha={resultado.falha} />
      </Moldura>
    );
  }

  const pagina = resultado.dados;
  // Mesmo cálculo da listagem pública (`lib/explorar/consulta.ts`): `total` itens, `size` por
  // página, direto do envelope do contrato — nunca de meta inventada no cliente (RNF021).
  const totalPaginas = totalDePaginas(pagina.total, pagina.size);

  if (pagina.items.length === 0) {
    return (
      <Moldura>
        {pagina.total === 0 ? (
          <Vazio contaAtiva={contaAtiva} />
        ) : (
          <PageState
            kind="empty"
            title="Esta página não tem resultados"
            message={`A sua lista tem ${contagem(pagina.total)}, mas não há item nesta página: ela termina na página ${totalPaginas}.`}
            action={
              <Link
                className="sc-btn sc-btn--ghost sc-btn--sm"
                href={rotaPreservada(urlComPagina('/painel/materiais', totalPaginas))}
              >
                Ir para a última página
              </Link>
            }
          />
        )}
      </Moldura>
    );
  }

  return (
    <Moldura>
      <section className="mt-8" aria-labelledby="lista-materiais" data-state="success">
        <h2 className="sc-subhead" id="lista-materiais">
          Seus materiais
        </h2>

        <p className="sc-helper" role="status">
          {contagem(pagina.total)} · página {pagina.page + 1} de {totalPaginas} · ordem: o que você
          alterou por último, primeiro
        </p>

        <p className="sc-helper sc-measure">
          <Link href="/painel/publicar">Publicar outro material</Link>. As ações de cada item
          dependem do estado de publicação dele e são decididas pelo servidor: esta tela oferece o
          caminho, e o que não for permitido é recusado com a razão (RNF010).
        </p>

        <ul className="sc-listagem">
          {pagina.items.map((material) => (
            <li
              key={material.slug}
              data-material={material.slug}
              data-estado={material.estado}
            >
              <CardMaterialProprio material={material} contaVerificada={contaAtiva} />
            </li>
          ))}
        </ul>

        <Pagination
          page={pagina.page + 1}
          totalPages={totalPaginas}
          buildHref={(alvo) => urlComPagina('/painel/materiais', alvo)}
          label="Paginação dos seus materiais"
        />
      </section>
    </Moldura>
  );
}

/** Contagem anunciada por TEXTO, não por cor nem por número solto (RNF007). */
function contagem(total: number): string {
  return total === 1 ? '1 material seu' : `${total} materiais seus`;
}

/** Casca da página: cabeçalho com a identidade da tela, igual em todos os estados. */
function Moldura({ children }: { children: ReactNode }) {
  return (
    <div className="sc-page">
      <header className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Conta</i> materiais próprios
        </p>
        <h1 className="sc-h1">Materiais próprios</h1>
        <p className="sc-sec__lead">
          Tudo o que você publicou, com o estado de publicação de cada material: o que está no ar,
          o que você retirou e o que você excluiu. Editar, retirar, repor no público e excluir
          acontecem aqui — e a lista do dono não esconde o que saiu do público, porque quem
          publicou é quem precisa saber o que aconteceu com o material.
        </p>
      </header>
      {children}
    </div>
  );
}

/**
 * Estado `vazio` — nunca tela branca (DRS §15.3), e sempre com o PRIMEIRO PASSO (RF015).
 *
 * Dois casos, porque são situações diferentes e a segunda não pode virar "publique!" para
 * quem não pode publicar: conta `ATIVO` orienta a publicar; conta pendente diz a verdade —
 * o primeiro passo é confirmar o e-mail, e o caminho para reenviar está na área do usuário.
 * Mandar a pessoa a uma tela que a bloquearia seria a armadilha que o §11 do
 * `PAGE_BLUEPRINTS.md` proíbe na publicação.
 */
function Vazio({ contaAtiva }: { contaAtiva: boolean }) {
  return (
    <div data-aviso="vazio-primeiro-passo" data-estado-conta={contaAtiva ? 'ATIVO' : 'PENDENTE_VERIFICACAO'}>
      <PageState
        kind="empty"
        title="Você ainda não publicou nenhum material"
        message={
          contaAtiva
            ? 'Nada publicado ainda — é o estado normal de quem acabou de chegar. Esta lista vai mostrar os seus materiais com o estado de publicação e as ações de cada um; ela fica vazia até a primeira publicação.'
            : 'Nada publicado ainda, e há um passo antes do primeiro material: a conta está com o e-mail pendente de confirmação, e publicar é a única operação que exige e-mail confirmado (ADR-0003).'
        }
        details={
          contaAtiva ? (
            <p className="sc-body sc-measure">
              O primeiro passo é <Link href="/painel/publicar">publicar um material</Link>: um PDF
              com título, descrição, categoria, tipo e licença. A autoria é a sua conta e a licença
              é escolha sua — as duas ficam visíveis na página pública, e o material aparece nesta
              lista assim que for criado.
            </p>
          ) : (
            <p className="sc-body sc-measure">
              A confirmação e o reenvio do link estão na{' '}
              <Link href="/painel">área do usuário</Link>, no estado da conta. Enquanto a
              confirmação não acontece, publicar fica bloqueado; navegar, buscar, abrir materiais e
              editar o perfil continuam liberados agora mesmo.
            </p>
          )
        }
        action={
          contaAtiva ? (
            <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/painel/publicar">
              Publicar o primeiro material
            </Link>
          ) : (
            <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/painel">
              Confirmar o e-mail para poder publicar
            </Link>
          )
        }
      />
    </div>
  );
}

/**
 * Resposta que não trouxe a lista → o estado do §15.3 que corresponde à CAUSA.
 *
 * 401 e 403 não deveriam acontecer depois de a sessão ter sido confirmada, mas acontecem: a
 * sessão pode cair entre as duas leituras, e a plataforma pode recusar o acesso. O
 * mapeamento existe para não mentir — dizer "falha de conexão" quando o servidor respondeu
 * 403 manda a pessoa conferir a internet por um defeito que não é dela. O resto (sem
 * resposta, resposta fora do contrato, 5xx) é `erro de rede/servidor`, com "tentar de novo".
 */
function EstadoDaFalha({ falha }: { falha: Falha }) {
  if (falha.motivo === 'http') {
    if (falha.status === 401) {
      return (
        <PageState
          kind="session-expired"
          title="Sua sessão expirou"
          message="A sessão deixou de valer antes de a lista ser lida, então os seus materiais não foram carregados. Entre novamente: o endereço desta página é preservado e você volta para ela."
          loginDestination="/painel/materiais"
        />
      );
    }
    if (falha.status === 403) {
      return (
        <PageState
          kind="unauthorized"
          title="A plataforma não liberou o acesso a esta lista"
          message="O servidor respondeu que esta conta não pode ler estes materiais. A lista é da própria conta que pede, então isso costuma significar sessão de outra conta ou permissão revogada — entre novamente com a conta correta."
        />
      );
    }
    if (falha.status === 404) {
      return (
        <PageState
          kind="not-found"
          title="A listagem dos seus materiais não foi encontrada"
          message="O servidor não reconheceu o endereço desta superfície. Os seus materiais não foram alterados; recarregue a página e, se continuar, avise a equipe."
        />
      );
    }
  }

  return (
    <PageState
      kind="network-error"
      title="Não foi possível carregar os seus materiais"
      message="A resposta do servidor não chegou ou veio fora do formato do contrato. A lista não foi exibida justamente para não mostrar um acervo que não é o real — nada foi alterado. Tente de novo; se continuar, tente outra vez em alguns minutos."
      action={<Recarregar />}
    />
  );
}
