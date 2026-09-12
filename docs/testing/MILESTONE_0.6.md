# Verificacao do milestone 0.6

Status: **IMPLEMENTADO, VERIFICADO E PERSISTIDO NO REMOTO** no commit
`4fba025`.

## Namespace pre-release

Antes da consolidacao, todos os packages foram migrados de `io.github.rizoma`
para `io.github.felipemacedo1.rizoma`, alinhados ao `groupId` controlado. A
migracao inclui producao, testes, exemplos, manifests, scripts e JavaDoc. Busca
estatica encontrou zero imports/declaracoes antigas e a inspecao dos JARs apos
`clean verify` encontrou zero entradas `io/github/rizoma/`. Nao existe bridge
de compatibilidade porque nunca houve release ou consumidor externo conhecido.

## Teste de consumidor externo

`rizoma-adoption-tests` declara como dependencia de producao somente o artefato
agregador `rizoma`. Quatro exemplos de integracao compilados cobrem simple
processing, workflow controlado, validator customizado e layout conhecido; um
quinto programa mede overhead end-to-end. O teste black-box
exercita:

- CSV -> `REVIEW_REQUIRED` -> confirmacao -> dry run `SUCCESS`;
- XLSX sintetico pelo mesmo `Rizoma.process()`;
- template conhecido -> `FAST_REUSE` e novo fingerprint;
- transformer e validator customizados pela Extension API;
- valor invalido -> `INVALID`, sem excecao tecnica;
- fonte nao suportada -> `FAILED` na Simple API e `InvalidSourceException` na
  Workflow API;
- source por InputStream limitado e observer sem dados de celula.

Foram executados oito testes black-box, inclusive rejeicao de plano ligado a
outro conteudo e confirmacao de que colunas explicitamente ignoradas chegam ao
`MappingPlan`. Esse ultimo teste encontrou e preveniu uma regressao real no
helper publico `plan(...)` durante a revisao.

## Regressao

`./mvnw clean verify` passou no OpenJDK 21.0.12 e no JBR OpenJDK 25.0.4,
sempre com `--release 21`: 108 testes, zero falhas e zero skips. O total e 58
core, 6 CSV, 11 Excel, 5 pt-BR, 20 CLI e 8 de adocao. Os testes CLI incluem os
corpora 0.2/0.4, compatibilidade JSON, plan/dry-run e layout 0.5.

JaCoCo do core mediu 2.397/2.530 linhas (94,74%) e 1.490/1.860 branches
(80,11%). Os gates 85%/80% passaram sem exclusoes artificiais. A camada de
adocao e exercitada no modulo black-box, fora do calculo historico do gate do
core.

O quickstart completo passou: analyze/explain, feedback explicito, plan/dry-run,
template, `FAST_REUSE` e novo plano ligado ao segundo arquivo. O corpus 0.2
permaneceu em 22/23 top-1, 23/23 top-3, 7/7 abstencoes e 5/5 no-match; o caso
`Registro X` continua incorreto. O corpus 0.4 preservou top-1 3/4, top-3 3/4
para 4/4, um caso melhorado, um piorado e dois inalterados.

## Performance

O facade nao materializa datasets nem copia resultados detalhados; resumos sao
limitados pelo proprio `AnalysisResult`/`DryRunResult`.

Um smoke sintetico de 100.000 linhas/5.000.033 bytes no Java 21 com
`-Xmx256m` comparou chamadas detalhadas e a fachada no mesmo processo:

| Fluxo | Workflow API | Simple API |
|---|---:|---:|
| analyze/full | 5.127 ms | 4.648 ms |
| plano confirmado + dry run | 1.054 ms | 646 ms |
| reconhecimento fast + dry run | 609 ms | 587 ms |

O processo levou 12,90 s, RSS maximo 180.020 KiB e maior heap observado antes
de GC 49.846 KiB. Ordem, cache e aquecimento favorecem as chamadas posteriores;
portanto os numeros apenas mostram ausencia de overhead evidente, nao provam
superioridade nem constituem benchmark ou gate de latencia.

O probe e reproduzido por `scripts/api-overhead-smoke.sh` depois de
`./mvnw verify`; o CSV de 100 mil linhas e gerado em streaming e removido ao
termino.

O volume de 1.000.000 linhas/50.000.034 bytes tambem passou no Java 21 sob
`-Xmx256m`: full analyze 51,50 s, fast 1,02 s, adaptive 1,01 s e dry run
7,32 s. RSS maximo por processo foi 169.248/101.404/105.268/193.292 KiB;
maior heap observado antes de GC foi 52.400/20.529/21.553/49.227 KiB. Fast
avaliou zero pares, adaptive um par, e o dry run confirmou 1.000.000 de linhas
validas, 1.000.000 de transformacoes e 4.000.000 de validacoes.

Contra a repeticao 0.5 no mesmo host (50,21/1,06/1,08/7,32 s), a variacao foi
pequena e nao indica regressao relevante da fachada. Isso continua sendo smoke
end-to-end, nao JMH; RSS e memoria total do processo e o valor de GC nao e pico
exato de heap.

A repeticao posterior a migracao de namespace confirmou novamente 1.000.000 de
linhas: analyze 53,88 s, fast 1,08 s, adaptive 1,09 s e dry run 7,65 s, sob o
mesmo `-Xmx256m`. A variacao de timing permanece compativel com um smoke
end-to-end e nao indica mudanca funcional causada pelo rename.
