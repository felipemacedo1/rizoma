# Estado atual

Atualizado em: 2026-09-11

## Confirmado

- Java 21 e o baseline, compilado com `--release 21`; Java 25 e a matriz de
  compatibilidade. Nenhum recurso preview e usado.
- Maven 3.9.16 esta fixado no Wrapper.
- A arquitetura e um monolito modular de biblioteca: `rizoma-core`,
  `rizoma-format-csv`, `rizoma-format-excel`, `rizoma-locale-ptbr` e
  `rizoma-cli` possuem codigo util e testes. O core depende apenas do JDK.
- Apache-2.0 e a licenca vigente; `NOTICE` preserva a atribuicao do projeto.
- O schema de destino permanece no formato 1.0. O `AnalysisResult` 0.2 produz
  JSON 1.2; a CLI `explain` continua aceitando relatorios 1.0 e 1.1.

## Estado de implementacao

**IMPLEMENTADO, VERIFICADO LOCALMENTE E PERSISTIDO NO REMOTO:** incrementos
0.1a e 0.1b, acrescidos do hardening da candidata 0.1, no commit base
`098628a` (que sucede o hardening `823a0cd`). CSV, XLS e XLSX compartilham API,
pipeline e CLI; nenhuma tag ou release publica foi criada.

**IMPLEMENTADO E VERIFICADO LOCALMENTE, AINDA NAO CONSOLIDADO EM COMMIT:**
milestone 0.2. O perfil agora inclui cardinalidade exata limitada ou HLL,
top-K Space-Saving, entropia, distribuicoes, estatistica numerica incremental,
mistura de tipos e anomalias protegidas. Jaccard, Jaro, Jaro-Winkler, trigrama
e cosine integram um subscore lexical correlacionado. Evidencia semantica separa
forma, validade e confiabilidade; schemas grandes usam pruning explicavel. Um
corpus sintetico reproduzivel mede ranking, abstencao, no-match, auto-map
simulado e confusao semantica. O relatorio JSON atual e 1.2.

**PLANEJADO / NAO IMPLEMENTADO:** detector completo de CNPJ e CEP,
transformacao, validacao de importacao, dry run, destinos, feedback, Hungarian,
plugins dinamicos, paralelismo, ML, embeddings e LLM. JMH foi conscientemente
adiado ate o subscore lexical estabilizar.

## Evidencias locais do 0.2

- `clean verify` final passou no OpenJDK 21.0.12 e no Temurin 25.0.4.1 LTS:
  57 testes, zero falhas e compilacao `release 21` nas duas execucoes.
- JaCoCo do core: 988/1.046 linhas (94,46%) e 611/762 branches (80,18%);
  gates 85%/80% atendidos sem exclusoes artificiais.
- Corpus: cinco datasets, 28 colunas, 23 mappings esperados e cinco no-match.
  O modelo completo obteve 22/23 top-1, 23/23 top-3, 7/7 abstencoes esperadas,
  5/5 no-match e 1/1 auto-map simulado correto. Esses numeros nao sao
  calibracao estatistica.
- O erro preservado e `Registro X` de fornecedor: sem detector CNPJ e sem pista
  lexical, `supplier.code` fica em primeiro e `supplier.document` no top-3.
- A ablation nao mostrou ganho de top-1 sobre o baseline 0.1 neste corpus. O
  ganho medido foi a abstencao esperada, de 6/7 para 7/7, sem falso positivo
  forte observado no conjunto pequeno.
- Volume 0.2: 1.000.000 registros, 3 colunas, 50.000.034 bytes, Java 21,
  `-Xms32m -Xmx256m`, 36,46 s, RSS maximo 172.172 KiB e maior heap observado
  antes de GC 51.735 KiB. Uma repeticao mediu 36,56 s/165.724 KiB; o baseline
  0.1 foi 36,88 s/162.364 KiB. Isso e teste de volume, nao benchmark controlado
  nem medida exata de pico de heap.

## Limites e riscos atuais

- GitHub Actions permanece bloqueado por billing antes de iniciar jobs. E uma
  pendencia operacional conhecida, separada da qualidade local, e nao bloqueia
  o desenvolvimento. Nao tentar resolve-la nesta etapa.
- O corpus e pequeno e sintetico; nao justifica chamar score ou
  `confidenceIndex` de probabilidade. A calibracao permanece `UNCALIBRATED` e
  `AUTO_MAP` continua desabilitado por padrao no core.
- Commons CSV entrega o campo depois de aloca-lo. O limite de bytes e antecipado,
  mas `maxFieldChars` e verificado apos tokenizacao; hardening anterior a
  alocacao permanece como risco conhecido.
- XLS legado usa HSSF em memoria com teto default de 20 MiB. XLSX faz streaming
  da worksheet, mas a shared strings table do POI e materializada e limitada
  indiretamente pelo teto expandido por entrada.
- HLL `p=10` declara erro relativo esperado de 3,25%. Space-Saving e entropia
  aproximada sao marcados `ESTIMATED`; nenhuma aproximacao e publicada como
  exata.
- A versao de desenvolvimento e `0.2.0-SNAPSHOT`. Nenhum artefato foi publicado.

## Proximo passo

Revisar o diff final e consolidar o milestone 0.2 somente quando commit/push
forem solicitados. Nao iniciar o 0.3 nesta etapa. A pendencia do CI remoto
permanece registrada sem bloquear trabalho local.
