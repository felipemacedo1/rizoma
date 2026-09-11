# AGENTS.md

## Missao

Projetar e implementar o Rizoma: um motor Java confiavel, inteligente e
automatizado para importacao de planilhas e CSV.

## Estado inicial

- O produto ainda nao foi implementado.
- A unica decisao tecnica fechada e a linguagem Java.
- Java 21 ou superior e uma hipotese forte, nao uma decisao definitiva.
- Framework, build tool, modulos, interfaces e persistencia devem ser definidos
  a partir dos requisitos, com justificativas registradas no memory bank.

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

## Primeira tarefa do agente

1. Elicitar ou inferir os casos de uso essenciais.
2. Comparar alternativas minimas de arquitetura e toolchain Java.
3. Decidir e registrar a versao Java e o build tool.
4. Definir o primeiro incremento vertical e seus criterios de aceite.
5. Implementar somente depois dessas decisoes.

## Criterio de conclusao

Antes de concluir uma entrega, execute testes e validacoes proporcionais ao
risco, atualize a documentacao afetada e registre o estado em
`docs/memory-bank/CURRENT.md`.

## Memory bank

- `CURRENT.md`: estado presente, riscos e proximo passo.
- `DECISIONS.md`: decisoes arquiteturais confirmadas, com contexto e motivo.
- `LEARNINGS.md`: fatos e falhas reutilizaveis confirmados.
- Atualize apenas conhecimento duravel; nunca registre segredos ou suposicoes.
