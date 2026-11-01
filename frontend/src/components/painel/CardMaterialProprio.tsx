import Link from 'next/link';
import { AcoesDoMaterial } from './AcoesDoMaterial';
import { Badge, type BadgeTone } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { dataCurta } from '@/lib/format';
import { ESTADO_DO_MATERIAL, acoesDoEstado } from '@/lib/materiais/acoes';
import { rotaMaterial } from '@/lib/rotas';
import type { EstadoDeMaterial, MaterialProprio } from '@/lib/api/tipos';

/**
 * Card de um material PRÓPRIO na lista de gerenciamento (RF015, PAGE_BLUEPRINTS.md §12).
 *
 * Mostra o que o contrato garante em cada item e nada além: título, descrição, tipo,
 * categoria, licença EXPLICADA (`descricaoCurta`, RN006), o ESTADO de publicação com a
 * leitura do que ele significa, as duas datas e o destino do arquivo.
 *
 * Diferenças deliberadas em relação ao `CardMaterial` da descoberta pública — e são
 * diferenças de superfície, não de estilo:
 *
 *   - **não há autoria**: o `MaterialProprio` não tem campo de autor, porque a autoria é
 *     sempre o dono (RN001). Um "por você mesmo" seria um campo com um valor só;
 *   - **o estado aparece**, e nos três valores. A descoberta pública só mostra o que está
 *     `PUBLICADO`; aqui o dono vê o que retirou e o que excluiu (ADR-0011 §2);
 *   - **o link só existe quando o material está no público**: `RETIRADO` e `EXCLUIDO`
 *     respondem 404 na página pública (RN002), e um link que leva a 404 é uma promessa
 *     falsa. Em `RETIRADO`, a própria explicação do estado diz que a página responde
 *     "não encontrado" — a pessoa não fica procurando o motivo;
 *   - **a data de publicação não é a única**: `atualizadoEm` é a ordem desta lista
 *     (o contrato ordena por ela, `atualizadoEm` DESC) e é o que diz "o que mexi por
 *     último, primeiro". Sem ela, a ordem pareceria aleatória.
 *
 * O tom do badge acompanha o estado, mas nunca é o único portador da informação (RNF007):
 * o rótulo em texto vem dentro do badge e a explicação inteira vem ao lado. `EXCLUIDO` sai
 * em tom neutro de propósito — não é erro nem alerta, é um estado terminal.
 */
const TOM_DO_ESTADO: Record<EstadoDeMaterial, BadgeTone> = {
  PUBLICADO: 'green',
  RETIRADO: 'yellow',
  EXCLUIDO: 'neutro',
};

export function CardMaterialProprio({
  material,
  contaVerificada,
}: {
  material: MaterialProprio;
  contaVerificada: boolean;
}) {
  const {
    slug,
    titulo,
    descricao,
    estado,
    categoria,
    tipo,
    licenca,
    arquivo,
    publicadoEm,
    atualizadoEm,
  } = material;
  const { rotulo, descricao: explicacao } = ESTADO_DO_MATERIAL[estado];

  // Título linkado SÓ quando a página pública existe (estado PUBLICADO). É a mesma regra
  // do botão explícito abaixo; o card do E5.7 (edição) é o outro caminho de saída daqui.
  const tituloDoCard =
    estado === 'PUBLICADO' ? (
      <Link href={rotaMaterial(slug)} data-link-titulo={slug}>
        {titulo}
      </Link>
    ) : (
      titulo
    );

  return (
    <Card as="article" eyebrow={tipo.nome} title={tituloDoCard} copy={descricao}>
      <div className="sc-card__meta">
        <p className="sc-helper">
          <Badge tone={TOM_DO_ESTADO[estado]}>{rotulo}</Badge>{' '}
          <span data-leitura-do-estado={estado}>{explicacao}</span>
        </p>

        <p className="sc-helper">
          Categoria: <strong>{categoria.nome}</strong> · Tipo: <strong>{tipo.nome}</strong> · Licença:{' '}
          <strong>{licenca.nome}</strong> — {licenca.descricaoCurta}
        </p>

        <p className="sc-helper">
          Publicado em <time dateTime={publicadoEm}>{dataCurta(publicadoEm)}</time> · Atualizado em{' '}
          <time dateTime={atualizadoEm}>{dataCurta(atualizadoEm)}</time>
        </p>

        <p className="sc-helper" data-arquivo={arquivo.disponivel ? 'disponivel' : 'removido'}>
          Arquivo:{' '}
          <span className="sc-mono">{arquivo.nomeOriginal}</span> —{' '}
          {arquivo.disponivel
            ? 'guardado e servido na página pública.'
            : 'já removido do armazenamento pela retenção de 7 dias; os dados do material continuam aqui.'}
        </p>

        {estado === 'PUBLICADO' ? (
          <p>
            <Link
              className="sc-btn sc-btn--ghost sc-btn--sm"
              href={rotaMaterial(slug)}
              data-link-publico={slug}
            >
              Abrir a página pública
              <span className="sc-visually-hidden"> do material {titulo}</span>
            </Link>
          </p>
        ) : null}
      </div>

      <AcoesDoMaterial
        slug={slug}
        titulo={titulo}
        estado={estado}
        contaVerificada={contaVerificada}
      />

      {/* Estado terminal: a ausência de ação é EXPLICADA aqui, no servidor, e não dentro do
          componente de cliente — que devolve `null` quando não há ação nem resultado a mostrar.
          Uma linha muda no lugar dos botões viraria a pergunta "e agora?"; o texto responde
          (ADR-0011 §6). Ele não some depois de uma exclusão feita nesta tela porque não depende
          de estado de cliente nenhum: é do material, não da sessão de cliques. */}
      {acoesDoEstado(estado).length === 0 ? (
        <p className="sc-helper mt-4" data-sem-acao={slug}>
          <strong>Nenhuma ação neste estado.</strong> Excluído é um estado terminal nesta versão da
          plataforma — não existe restauração, nem por você nem pela equipe. O que continua aqui são
          os dados que descrevem o material; o arquivo já saiu do armazenamento.
        </p>
      ) : null}
    </Card>
  );
}
