# Verificacao do milestone 0.5

Este documento registra metodo e resultados observados do Adaptive Layout &
Data Projection. Todas as fixtures sao sinteticas.

## Cenarios funcionais

- mesmo layout/novo conteudo: `EXACT` + `FAST_REUSE`, zero candidate pairs e
  zero similarity metrics;
- ordem diferente com IDs unicos: `COMPATIBLE` + `FAST_REUSE` e rebind por ID;
- uma renomeacao compativel: `ADAPTIVE_REANALYSIS`, um binding afetado;
- coluna adicional: adaptive e coluna fica `NO_MATCH_FOUND` ate confirmacao;
- dependencia removida, tipo contraditorio, header duplicado ou semantica atual
  contraditoria: `FULL_ANALYSIS`, sem plano reutilizado;
- NoOp registry: `UNKNOWN` + `FULL_ANALYSIS`;
- source/constant/derived/ignored/unmapped, seis operacoes, locale, divisao por
  zero e contratos invalidos;
- MappingPlan 1.0 e 1.1 sem `projectionSource` continuam legiveis como binding
  direto.

## Evidencia executada

`clean verify` passou em OpenJDK 21.0.12 e JBR OpenJDK 25 (`25+36-b176.4`), com
99 testes. JaCoCo mediu 94,68% de linhas e 80,08% de branches no core. O corpus
0.2 permaneceu 22/23 top-1 e 23/23 top-3; o corpus de feedback permaneceu top-1
3/4 e top-3 3/4 -> 4/4, incluindo o caso que piora.

O script
`scripts/volume-smoke.sh` gera um CSV de 1 milhao de linhas, mede separadamente
FULL analyze, FAST recognition, ADAPTIVE recognition e dry run, e valida rota,
linhas, candidate pairs, metric executions e fingerprints sob `-Xmx256m`.

Timing wall-clock e RSS sao medidas end-to-end de uma execucao local, nao JMH
nem gate portavel. O maior heap observado em log de GC e pre-coleta, nao pico
exato. O fast path ainda percorre os bytes para calcular SHA-256 e le ate 64
registros para guardas; ele evita profiling completo, candidate generation e
similarity scoring global.

Repeticao final em OpenJDK 21.0.12, 50.000.034 bytes e `-Xmx256m`:

| Rota | Tempo | RSS maximo | Heap GC observado | Trabalho |
|---|---:|---:|---:|---|
| FULL analyze 0.5 | 50,21 s | 174.460 KiB | 59.516 KiB | 1M rows/profile completo |
| FAST_REUSE | 1,06 s | 99.368 KiB | 20.793 KiB | 64 rows, 0 pares/metricas |
| ADAPTIVE_REANALYSIS | 1,08 s | 100.284 KiB | 20.796 KiB | 64 rows, 1 par afetado |
| dry run | 7,32 s | 200.880 KiB | 53.354 KiB | 1M rows validas |

O commit-base 0.4, extraido em diretorio temporario e executado imediatamente
no mesmo host/JDK, mediu analyze 50,17 s e dry run 6,96 s. Isso nao mostra
regressao do caminho tradicional no comparativo controlado. Os tempos menores
historicos foram obtidos em outra condicao de carga. FAST/ADAPTIVE ainda fazem
I/O completo para SHA-256; o ganho observado vem do profiling e scoring evitados.

## Limitacoes preservadas

- sampling pode nao observar drift raro ou localizado fora do guard;
- adaptive cobre alteracao localizada segura, nao um diff geral de N colunas;
- nao existe registry persistente de layouts;
- matching global/Hungarian permanece adiado;
- CNPJ, detector semantico de CEP, quantis/outliers robustos e unique/FK
  permanecem ausentes;
- `AUTO_MAP` continua desligado e `calibration=UNCALIBRATED`;
- nenhuma rota escreve em destino e JMH continua adiado.
