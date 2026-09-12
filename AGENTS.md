# AGENTS.md

## Missao

Projetar e implementar o Rizoma: um motor Java confiavel, inteligente e
automatizado para importacao de planilhas e CSV.

## Contexto historico

O repositorio nasceu sem implementacao e apenas com Java definido. A etapa de
especificacao encerrou as decisoes iniciais; este contexto nao substitui o
estado vigente.

## Decisoes vigentes

- Baseline Java 21 com `--release 21`, sem preview, e CI tambem em Java 25.
- Maven 3.9.x fixado pelo Maven Wrapper.
- Namespace publico: `io.github.felipemacedo1.rizoma.*`, alinhado ao groupId
  controlado `io.github.felipemacedo1`; nao reintroduzir `io.github.rizoma.*`.
- Monolito modular de biblioteca, core somente JDK e adaptadores
  separados para CSV, Excel, locale pt-BR e CLI.
- Pipeline deterministico, explicavel, sequencial e limitado em memoria.
- Especificacao: `docs/architecture/TECHNICAL_SPECIFICATION.md`.
- Estado e proximo passo: `docs/memory-bank/CURRENT.md`.

## Forma de trabalho

- Comece lendo `README.md` e `docs/memory-bank/CURRENT.md`.
- Investigue antes de perguntar; pergunte apenas quando uma escolha mudar
  materialmente o produto e nao puder ser inferida com seguranca.
- Prossiga autonomamente enquanto houver uma proxima acao segura e objetiva.
- Prefira a menor arquitetura que entregue um incremento vertical utilizavel.
- Nao implemente grandes camadas especulativas.
- Mantenha leitura, mapeamento, validacao, transformacao e persistencia separaveis.
- Toda inferencia deve ser explicavel, testavel e auditavel.
- Nao envie dados importados, credenciais ou dados pessoais a servicos externos
  sem autorizacao explicita.
- Nao use force-push nem reescreva historico compartilhado.
- Commits e push fazem parte da entrega quando solicitados.

## Proxima tarefa executavel

Os milestones 0.5 e 0.6 estao implementados e verificados localmente no
worktree; o conjunto deve ser revisado/consolidado somente quando solicitado.
O remoto ainda aponta para o 0.4 conforme verificacao de
2026-09-12. Matching global/Hungarian esta adiado por decisao evidence-driven.
O bloqueio de cobranca do GitHub Actions e uma pendencia operacional conhecida
e nao impede desenvolvimento local verificado; nao gastar tempo com esse
bloqueio ate nova orientacao.

## Criterio de conclusao

Antes de concluir uma entrega, execute testes e validacoes proporcionais ao
risco, atualize a documentacao afetada e registre o estado em
`docs/memory-bank/CURRENT.md`.

## Memory bank

- `CURRENT.md`: estado presente, riscos e proximo passo.
- `DECISIONS.md`: decisoes arquiteturais confirmadas, com contexto e motivo.
- `LEARNINGS.md`: fatos e falhas reutilizaveis confirmados.
- Atualize apenas conhecimento duravel; nunca registre segredos ou suposicoes.
