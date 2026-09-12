# Verificacao do milestone 0.3

## Escopo

A suite cobre transformers e validators, plano/fingerprints, policies de erro,
limites, mascaramento, fechamento, API Java, CLI CSV/XLSX e regressao do corpus
0.2. A fixture `examples/dry-run-customers.csv` e integralmente sintetica.

Comandos de aceite:

```bash
./mvnw clean verify
JAVA_HOME=/caminho/do/jdk25 PATH="$JAVA_HOME/bin:$PATH" ./mvnw clean verify
./scripts/quickstart.sh
./scripts/volume-smoke.sh
```

`volume-smoke.sh` gera um CSV de um milhao de registros em streaming, executa
analyze e dry-run em JVMs separadas com `-Xms32m -Xmx256m`, valida contagens dos
dois JSONs e reporta `/usr/bin/time -v` e o maior heap observado antes de GC.
RSS e memoria total do processo; a observacao de GC nao e pico exato de heap.

## Resultado atual

- OpenJDK 21.0.12: `./mvnw clean verify`, 77 testes, zero falhas.
- Temurin 25.0.4.1 LTS: mesmo comando com `JAVA_HOME`/`PATH`, 77 testes, zero
  falhas e compilacao `--release 21`.
- Core JaCoCo: 1.470/1.562 linhas (94,11%) e 932/1.159 branches (80,41%).
- Quickstart: analysis 20 linhas; fixture dry run 3 linhas, 2 validas, 1
  invalida, 7 erros em campos distintos e zero valores brutos procurados nos
  JSONs.
- Corpus 0.2: 22/23 top-1, 23/23 top-3, 7/7 abstencoes, 5/5 no-match; a falha
  `Registro X -> supplier.code` continua presente.
- Volume Java 21, 1.000.000 registros, 3 colunas e 50.000.034 bytes:
  - analyze: 34,94 s, RSS 174.784 KiB, heap observado 56.946 KiB;
  - dry run: 4,82 s, RSS 202.092 KiB, heap observado 49.834 KiB;
  - dry run produziu 1.000.000 linhas validas, 1.000.000 transformacoes e
    4.000.000 validacoes, sem erro, sob `-Xmx256m`.

O baseline analyze 0.2 para o mesmo gerador foi 36,46 s/RSS 172.172 KiB/heap
observado 51.735 KiB. O analyze 0.3 desta repeticao ficou cerca de 4,2%
mais rapido, com RSS e heap observado maiores. Variacao de uma execucao nao
local nao permite atribuir causalidade; esses resultados sao smoke tests de
volume, nao microbenchmarks.
