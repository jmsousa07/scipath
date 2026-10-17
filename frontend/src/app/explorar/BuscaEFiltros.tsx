'use client';

import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState, useTransition } from 'react';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { LIMITE_BUSCA, ORDENS, urlExplorar } from '@/lib/explorar/consulta';
import { rotaExplorar } from '@/lib/rotas';
import type { ItemTaxonomia, OrdemMateriais } from '@/lib/api/tipos';

/**
 * Busca e filtros de `/explorar` (RF002, RF003, RF004).
 *
 * Decisões desta tela (o que o card exige):
 *
 *   1. **A busca espera antes de disparar.** Disparar por caractere seria uma requisição
 *      por tecla; a espera de 400 ms depois da última tecla resolve — e, como a espera é
 *      do lado do cliente, a indicação de "buscando" existe para a pessoa saber que o
 *      pedido saiu (RNF004: nada de mudança silenciosa).
 *   2. **A URL é a fonte da verdade.** Nada de estado de filtro só dentro do componente:
 *      o destino de toda navegação sai de `urlExplorar(...)` (módulo puro), então a URL
 *      que a pessoa copia reproduz a mesma lista e o voltar/avançar do navegador funciona.
 *   3. **Um formulário GET de verdade.** Sem JavaScript o navegador submete o formulário
 *      e a tela continua funcionando; com JavaScript o mesmo formulário navega pelo
 *      roteador. Não existe caminho "só funciona com JS".
 *   4. **Campo vazio não vira parâmetro** (`urlExplorar` omite padrão e vazio): uma única
 *      URL por listagem.
 *   5. **Filtro novo volta para a primeira página** — trocar de filtro estando na página 5
 *      levaria a uma página que talvez não exista.
 */

/** Espera antes de disparar a busca: nunca uma requisição por tecla (RF003). */
const ESPERA_MS = 400;

type Campos = {
  q: string;
  categoria: string;
  tipo: string;
  ordem: OrdemMateriais;
};

/** Assinatura dos campos: diz se o que está na tela já é o que está na URL. */
function assinatura(campos: Campos): string {
  return [campos.q.trim(), campos.categoria, campos.tipo, campos.ordem].join('\u0000');
}

export type BuscaEFiltrosProps = {
  categorias: ItemTaxonomia[];
  tipos: ItemTaxonomia[];
  /** estado atual da URL, que é o que manda: o formulário parte daqui */
  params: { q: string; categoria: string; tipo: string; ordem: OrdemMateriais };
};

export function BuscaEFiltros({ categorias, tipos, params }: BuscaEFiltrosProps) {
  const router = useRouter();
  const [pendente, iniciar] = useTransition();

  const doServidor: Campos = {
    q: params.q,
    categoria: params.categoria,
    tipo: params.tipo,
    ordem: params.ordem,
  };
  const assinaturaDoServidor = assinatura(doServidor);

  const [campos, setCampos] = useState<Campos>(doServidor);
  /**
   * Última assinatura que ESTA tela levou para a URL. Enquanto o valor pedido não volta
   * do servidor, o efeito de sincronização ignora o que chega: sem isso, a resposta de um
   * pedido anterior apagaria o que a pessoa digitou depois dele (o texto voltaria no meio
   * da digitação).
   */
  const assinaturaEnviada = useRef(assinaturaDoServidor);
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (assinaturaDoServidor === assinaturaEnviada.current) return;
    assinaturaEnviada.current = assinaturaDoServidor;
    setCampos({ q: params.q, categoria: params.categoria, tipo: params.tipo, ordem: params.ordem });
  }, [assinaturaDoServidor, params.q, params.categoria, params.tipo, params.ordem]);

  const navegar = useCallback(
    (destino: Campos) => {
      const limpo: Campos = { ...destino, q: destino.q.trim() };
      // Já é o que esta tela levou para a URL (ou nada mudou desde ela): não navega de
      // novo — repetir a navegação jogaria fora a página em que a pessoa está e gastaria
      // um render do servidor à toa.
      if (assinatura(limpo) === assinaturaEnviada.current) return;
      assinaturaEnviada.current = assinatura(limpo);
      iniciar(() => {
        // URL montada com filtros em tempo de execução — daí o `as Route` (ver lib/rotas.ts).
        router.replace(rotaExplorar(urlExplorar({ ...limpo, pagina: 1 })), { scroll: false });
      });
    },
    [router],
  );

  function cancelarEspera() {
    if (temporizador.current === null) return;
    clearTimeout(temporizador.current);
    temporizador.current = null;
  }

  // Busca: só dispara depois da pausa, e nunca quando o campo já é o que está na URL.
  useEffect(() => {
    if (assinatura(campos) === assinaturaEnviada.current) return;
    temporizador.current = setTimeout(() => {
      temporizador.current = null;
      navegar(campos);
    }, ESPERA_MS);
    return cancelarEspera;
  }, [campos, navegar]);

  function aoMudar(campo: 'categoria' | 'tipo' | 'ordem', valor: string) {
    const proximos: Campos =
      campo === 'ordem'
        ? { ...campos, ordem: valor as OrdemMateriais }
        : campo === 'categoria'
          ? { ...campos, categoria: valor }
          : { ...campos, tipo: valor };
    setCampos(proximos);
    cancelarEspera();
    navegar(proximos);
  }

  return (
    <form
      className="sc-explorar__filtros"
      action="/explorar"
      method="get"
      onSubmit={(evento) => {
        // Com JavaScript o envio passa pelo roteador; sem JavaScript o navegador faz a
        // submissão GET normal, e este trecho simplesmente não roda.
        evento.preventDefault();
        cancelarEspera();
        navegar(campos);
      }}
    >
      <TextField
        id="filtro-busca"
        name="q"
        label="Buscar material"
        type="search"
        autoComplete="off"
        placeholder="Título ou descrição"
        hint={`Procura no título e na descrição. Acento e maiúscula não fazem diferença. Até ${LIMITE_BUSCA} caracteres.`}
        value={campos.q}
        onChange={(evento) => setCampos({ ...campos, q: evento.target.value })}
      />

      <CampoSelect
        id="filtro-categoria"
        nome="categoria"
        label="Categoria"
        opcaoVazia="Todas as categorias"
        valor={campos.categoria}
        opcoes={categorias}
        aoMudar={(valor) => aoMudar('categoria', valor)}
      />

      <CampoSelect
        id="filtro-tipo"
        nome="tipo"
        label="Tipo de material"
        opcaoVazia="Todos os tipos"
        valor={campos.tipo}
        opcoes={tipos}
        aoMudar={(valor) => aoMudar('tipo', valor)}
      />

      <CampoSelect
        id="filtro-ordem"
        nome="ordem"
        label="Ordenar por"
        valor={campos.ordem}
        opcoes={ORDENS.map((ordem) => ({ slug: ordem.valor, nome: ordem.rotulo }))}
        aoMudar={(valor) => aoMudar('ordem', valor)}
      />

      <div className="sc-explorar__acoes">
        <Button type="submit" variant="primary" size="sm">
          Aplicar filtros
        </Button>
        {/* Região `status` sempre presente: o anúncio só sai se a região já existe
            ANTES da mudança. Parada, ela fica vazia. */}
        <p className="sc-helper" role="status">
          {pendente ? 'Buscando resultados…' : ''}
        </p>
      </div>
    </form>
  );
}

/**
 * Campo de seleção do filtro.
 *
 * Por que não é um primitivo de `components/ui/`: o E1.7 entregou campo de texto, área de
 * texto e arquivo — o filtro é o primeiro lugar do produto que precisa de `select`. O
 * markup reusa `.sc-field` do contrato visual (rótulo associado, caixa, ajuda) e mora
 * aqui, colado ao único uso. Quando um segundo lugar precisar de seleção, vira primitivo
 * — card próprio.
 *
 * `nome` é separado do `id` de propósito: é o nome do parâmetro de consulta que o
 * navegador envia na submissão sem JavaScript (`categoria`, `tipo`, `ordem`), e não o
 * identificador do controle no documento.
 */
function CampoSelect({
  id,
  nome,
  label,
  opcaoVazia,
  valor,
  opcoes,
  aoMudar,
}: {
  id: string;
  nome: string;
  label: string;
  opcaoVazia?: string;
  valor: string;
  opcoes: ItemTaxonomia[];
  aoMudar: (valor: string) => void;
}) {
  return (
    <div className="sc-field">
      <label className="sc-field__label" htmlFor={id}>
        {label}
      </label>
      <div className="sc-field__box">
        <select id={id} name={nome} value={valor} onChange={(evento) => aoMudar(evento.target.value)}>
          {opcaoVazia ? <option value="">{opcaoVazia}</option> : null}
          {opcoes.map((opcao) => (
            <option key={opcao.slug} value={opcao.slug}>
              {opcao.nome}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
