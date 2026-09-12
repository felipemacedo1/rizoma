# Verificacao do milestone 0.4

## Corpus de feedback

`corpus/feedback/development` e usado somente para produzir eventos;
`corpus/feedback/evaluation` mede fontes separadas. Todos os dados sao
sinteticos. O teste gera `rizoma-cli/target/feedback-evaluation.json` com:

- ranking top-1/top-3 antes e depois;
- deltas de abstencao/decisao por caso;
- casos melhorados, piorados e inalterados;
- conflitos entre historico e evidencia atual;
- supressao de candidato rejeitado.

O conjunto controlado inclui confirmacao de codigo, rejeicao de e-mail,
correcao de documento de fornecedor, historico incorreto contra CPF forte e um
empate lexical com historico deliberadamente enganoso. O ultimo caso deve
permanecer: ele prova que historico pode piorar ranking e que o resultado nao e
uma demonstracao escolhida apenas para parecer favoravel.

Resultado verificado na implementacao atual:

| Medida | Sem history | Com history | Delta |
|---|---:|---:|---:|
| top-1 | 3/4 | 3/4 | 0 |
| top-3 | 3/4 | 4/4 | +1 |

Um caso melhorou, um piorou e dois ficaram inalterados. Houve um conflito
historico/semantico; CPF forte permaneceu no topo. O candidato de e-mail
rejeitado caiu de score `0,138076` para `0,112683`. Isso nao e calibracao nem
evidencia de ganho geral. As abstencoes permaneceram 2/4 antes e depois
(`abstentionDelta=0`).

## Performance e regressao

`KnowledgePerformanceTest` gera 20.000 eventos sinteticos em memoria e mede uma
analise sem knowledge, com um evento e com snapshot maior. O JSON em
`rizoma-cli/target/knowledge-performance.json` registra JDK, eventos e nanos.
E um timing de integracao sem gate absoluto, nao JMH; seu objetivo e detectar
mudanca grosseira e confirmar que o lookup indexado produz resultado.

Os comandos de aceite sao:

```bash
./mvnw clean verify
./scripts/quickstart.sh
./mvnw -pl rizoma-cli -am -Dtest=CorpusEvaluationTest,FeedbackEvaluationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/volume-smoke.sh
```

O volume continua com knowledge NoOp por default e, portanto, mede a regressao
do pipeline anterior. JMH permanece adiado. Resultados finais de JDK, cobertura,
timing de knowledge e volume ficam em `docs/memory-bank/CURRENT.md`; artefatos
gerados em `target/` nao sao versionados.

## Evidencia local final

- OpenJDK 21.0.12 e JBR OpenJDK 25 (`25+36-b176.4`): 89 testes, zero falhas ou
  skips, sempre com `--release 21`.
- Core: 94,58% de linhas e 80,33% de branches; gates 85%/80% atendidos sem
  exclusoes.
- Corpus 0.2 com NoOp: 22/23 top-1, 23/23 top-3, 7/7 abstencoes e 5/5
  no-match; `Registro X` continua incorreto em top-1.
- Timing Java 21 de uma execucao: 5,34 ms NoOp, 34,75 ms com um evento e
  176,04 ms incluindo construcao de snapshot com 20.000 eventos. Nao e JMH.
- Volume Java 21 sob `-Xmx256m`: analyze 37,97 s/RSS 162.696 KiB/heap observado
  52.087 KiB; dry run 5,47 s/RSS 202.840 KiB/heap observado 49.997 KiB. Todas
  as 1.000.000 linhas foram efetivamente processadas e validadas.
- Quickstart completo passou e confirmou que o JSON Lines nao continha o CPF
  bruto procurado.
