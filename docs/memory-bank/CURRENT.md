# Estado atual

Atualizado em: 2026-09-12

## Confirmado

- Java 21 e o baseline, compilado com `--release 21`; Java 25 e a matriz de
  compatibilidade. Nenhum recurso preview e usado.
- Maven 3.9.16 esta fixado no Wrapper.
- A arquitetura e um monolito modular de biblioteca: `rizoma-core`,
  `rizoma-format-csv`, `rizoma-format-excel`, `rizoma-locale-ptbr` e
  `rizoma-cli` possuem codigo util e testes. O agregador `rizoma` oferece a API
  de adocao e `rizoma-adoption-tests` simula o consumidor externo. O core
  depende apenas do JDK.
- Apache-2.0 e a licenca vigente; `NOTICE` preserva a atribuicao do projeto.
- O namespace Maven controlado e `io.github.felipemacedo1`; packages Java usam
  exclusivamente `io.github.felipemacedo1.rizoma.*`. O antigo
  `io.github.rizoma.*` foi removido antes da primeira release.
- O schema de destino permanece no formato 1.0. O `AnalysisResult` atual produz
  JSON 1.3; a CLI `explain` aceita relatorios 1.0, 1.1, 1.2 e 1.3.

## Estado de implementacao

**IMPLEMENTADO, VERIFICADO LOCALMENTE E PERSISTIDO NO REMOTO:** incrementos
0.1a e 0.1b, acrescidos do hardening da candidata 0.1, no commit base
`098628a` (que sucede o hardening `823a0cd`). CSV, XLS e XLSX compartilham API,
pipeline e CLI; nenhuma tag ou release publica foi criada.

**IMPLEMENTADO, VERIFICADO E PERSISTIDO NO REMOTO:** milestone 0.2 no commit
`e8acef6`. O perfil inclui cardinalidade exata limitada ou HLL,
top-K Space-Saving, entropia, distribuicoes, estatistica numerica incremental,
mistura de tipos e anomalias protegidas. Jaccard, Jaro, Jaro-Winkler, trigrama
e cosine integram um subscore lexical correlacionado. Evidencia semantica separa
forma, validade e confiabilidade; schemas grandes usam pruning explicavel. Um
corpus sintetico reproduzivel mede ranking, abstencao, no-match, auto-map
simulado e confusao semantica. O relatorio JSON produzido pelo 0.2 era 1.2.

**IMPLEMENTADO, VERIFICADO E CONSOLIDADO:** milestone 0.3. Esta atualizacao
integra o commit de consolidacao administrativa do milestone. `MappingPlan` 1.0 exige
confirmacoes e fica ligado a fingerprints;
transformers/validators tipados executam por linha; `DryRunResult` 1.0 publica
contagens e problemas limitados/mascarados. API e CLI `plan`/`dry-run` nao
possuem porta de destino e nao escrevem em sistema externo.

**IMPLEMENTADO, VERIFICADO E CONSOLIDADO:** milestone 0.4.
`MappingFeedback` 1.0 registra confirmacoes, rejeicoes e correcoes explicitas;
knowledge NoOp/InMemory e o adaptador JSON Lines fornecem snapshots limitados e
auditaveis. O scorer exibe history separado, `AnalysisResult` 1.3 e
`MappingPlan` 1.1 registram o snapshot, e a CLI oferece `feedback
confirm/reject/correct` e `analyze --knowledge`. Esta atualizacao integra o
commit de consolidacao administrativa do milestone.

**IMPLEMENTADO E VERIFICADO LOCALMENTE, AINDA NAO CONSOLIDADO:** milestone 0.5
Adaptive Layout & Data Projection. `LayoutSignature`/`LayoutTemplate` 1.0 e
registries NoOp/InMemory permitem `FULL_ANALYSIS`, `FAST_REUSE` e
`ADAPTIVE_REANALYSIS` com drift explicado. Todo reuse gera `MappingPlan` 1.2
novo, ligado ao fingerprint do conteudo atual. Projection suporta source,
constant, derived e unmapped; source ignorada permanece distinta de no-match.
Dry run executa seis operacoes declarativas seguras antes de transformers e
validators. CLI oferece `template create`, `recognize` e `explain-plan`.

**IMPLEMENTADO E VERIFICADO LOCALMENTE, AINDA NAO CONSOLIDADO:**
milestone 0.6 Public Java API & Adoption Layer. O artefato agregador `rizoma`
compoe CSV/XLS/XLSX/core/pt-BR; `Rizoma.create()` fornece defaults seguros e
`ProcessRequest`/`ProcessResult` distinguem review, dados invalidos e falha
tecnica. Workflow e Extension APIs permanecem acessiveis, a CLI usa a mesma
fachada e o modulo black-box depende diretamente apenas do agregador. A API e
experimental antes de 1.0; reutilizacao sequencial e suportada, mas concorrencia
depende das garantias das extensions injetadas.

**PLANEJADO / NAO IMPLEMENTADO:** detector completo de CNPJ e detector
semantico de CEP, unique/FK, qualquer destino/importacao, registry persistente
de layouts,
plugins dinamicos, paralelismo, ML, embeddings e LLM. JMH continua adiado.
Hungarian/matching global esta `DEFERRED / EVIDENCE-DRIVEN`, nao e a proxima
etapa presumida.

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

## Evidencias locais do 0.3

- `./mvnw clean verify` passou no OpenJDK 21.0.12 e no Temurin 25.0.4.1 LTS,
  sempre com `--release 21`: 77 testes, zero falhas.
- JaCoCo do core: 1.470/1.562 linhas (94,11%) e 932/1.159 branches (80,41%);
  gates 85%/80% atendidos sem exclusoes.
- O quickstart executou analyze/explain/plan/dry-run. A fixture 0.3 processou 3
  linhas: 2 validas, 1 invalida e 7 erros de campo; o JSON nao continha os
  valores brutos procurados.
- O corpus 0.2 permaneceu inalterado: 22/23 top-1, 23/23 top-3, 7/7 abstencoes,
  5/5 no-match e a falha `Registro X` preservada.
- Volume comparavel ao 0.2: 1.000.000 registros, 3 colunas, 50.000.034 bytes,
  Java 21 e `-Xmx256m`. Analyze 0.3: 34,94 s, RSS 174.784 KiB, maior heap
  observado antes de GC 56.946 KiB. Dry run: 4,82 s, RSS 202.092 KiB, heap
  observado 49.834 KiB, 1.000.000 validas, 1.000.000 transformacoes e
  4.000.000 validacoes. Medidas sao de uma execucao end-to-end, nao JMH nem
  pico exato de heap.

## Evidencias locais do 0.4

- `./mvnw clean verify` passou no OpenJDK 21.0.12 e no JetBrains Runtime
  OpenJDK 25 (`25+36-b176.4`), sempre compilando com `--release 21`: 89 testes,
  zero falhas ou skips.
- JaCoCo do core: 1.711/1.809 linhas (94,58%) e 1.082/1.347 branches (80,33%);
  gates 85%/80% atendidos sem exclusoes artificiais.
- O corpus 0.2 com knowledge NoOp permaneceu em 22/23 top-1, 23/23 top-3,
  7/7 abstencoes, 5/5 no-match e 1/1 auto-map simulado correto. A falha
  `Registro X -> supplier.code` permanece.
- Corpus de feedback separado: top-1 3/4 antes e depois, top-3 3/4 -> 4/4,
  abstencoes 2/4 -> 2/4; um caso melhorou, um piorou e dois nao mudaram.
  Rejeicoes reduziram o score de e-mail de 0,138076 para 0,112683. CPF forte
  venceu vinte confirmacoes historicas incorretas, com um conflito registrado.
- Timing de integracao Java 21, incluindo criacao inicial do snapshot: NoOp
  5,34 ms, um evento 34,75 ms e 20.000 eventos 176,04 ms. No JBR 25: 6,61 ms,
  21,51 ms e 155,94 ms. Sao medicoes unicas de integracao, nao JMH nem gates de
  latencia; lookup no snapshot construido e indexado por chave.
- Quickstart executou analyze/explain, gravacao explicita, nova analise com
  history, plan e dry-run; o knowledge persistido nao continha o CPF bruto.
- Volume Java 21, 1.000.000 registros, 3 colunas e 50.000.034 bytes sob
  `-Xmx256m`: analyze 37,97 s, RSS 162.696 KiB e heap observado 52.087 KiB;
  dry run 5,47 s, RSS 202.840 KiB e heap observado 49.997 KiB. Foram validadas
  1.000.000 linhas, 1.000.000 transformacoes e 4.000.000 validacoes. History
  estava NoOp, preservando o caminho anterior. RSS e memoria total; heap e a
  maior observacao antes de GC, nao pico exato.

## Evidencias locais do 0.5

- `./mvnw clean verify` passou no OpenJDK 21.0.12 e no JBR OpenJDK 25
  (`25+36-b176.4`), sempre compilando com `--release 21`: 99 testes, zero
  falhas ou skips.
- JaCoCo do core: 2.368/2.501 linhas (94,68%) e 1.483/1.852 branches (80,08%);
  gates 85%/80% atendidos sem exclusoes artificiais.
- Quickstart executou analyze/explain, feedback, plan/dry-run, template,
  reconhecimento `FAST_REUSE` e dry run do segundo arquivo. A rota fast avaliou
  zero candidate pairs e zero similarity metrics; o novo plano recebeu o
  fingerprint do segundo arquivo.
- Corpus 0.2 com NoOp permaneceu em 22/23 top-1, 23/23 top-3, 7/7 abstencoes e
  5/5 no-match; `Registro X -> supplier.code` continua preservado. Corpus 0.4
  permaneceu top-1 3/4, top-3 3/4 -> 4/4, com um caso melhorado, um piorado e
  dois inalterados.
- Volume Java 21: 1.000.000 registros, 3 colunas, 50.000.034 bytes e
  `-Xmx256m`. Na repeticao final, full analyze levou 50,21 s, RSS 174.460 KiB e
  heap observado 59.516 KiB; fast reuse 1,06 s, 99.368 KiB e 20.793 KiB;
  adaptive com um binding afetado 1,08 s, 100.284 KiB e 20.796 KiB; dry run
  7,32 s, 200.880 KiB e 53.354 KiB. Fast/adaptive leram 64 registros, evitaram
  profiling completo; fast executou 0 pares/metricas e adaptive 1 par. Sao
  medicoes end-to-end, nao JMH nem gates de latencia. Para isolar ambiente, o
  commit-base 0.4 foi extraido em temporario e executado no mesmo host/JDK:
  analyze 50,17 s e dry run 6,96 s. Portanto nao houve regressao observavel do
  analyze tradicional nesta comparacao controlada; os numeros historicos de
  37,97 s foram obtidos em outra condicao de carga.

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
- MappingPlan 1.2, LayoutTemplate/LayoutRecognitionResult 1.0 e DryRunResult
  1.0 sao contratos experimentais. Dry run cobre
  apenas regras locais configuradas e nao equivale a importacao/prontidao para
  producao.
- MappingPlan nao e assinado e e tratado como configuracao confiavel. Regexes
  customizadas usam `java.util.regex.Pattern`; custo patologico dentro de uma
  linha ainda nao possui timeout isolado.
- MappingFeedback 1.0, AnalysisResult 1.3 e o adapter de knowledge em arquivo
  continuam experimentais. History usa correspondencia exata do nome
  normalizado, sem decay, namespace organizacional dedicado, compactacao ou
  coordenacao multi-host. Um caso controlado prova que historico incorreto pode
  piorar ranking ambiguo.
- O arquivo JSON Lines e local/cooperativo, limitado a 10 MiB/100.000 eventos/
  16 KiB por linha nos defaults. Nao e storage transacional distribuido.
- Layout recognition usa guard default das primeiras 64 linhas; drift raro fora
  da amostra pode nao ser observado. SHA-256 ainda le todos os bytes. Adaptive
  cobre mudanca localizada, nao diff geral de multiplos bindings.
- Layout registry persistente nao existe; NoOp e default e InMemory e limitado.
  A CLI carrega um template JSON explicito por execucao.
- A versao de desenvolvimento e `0.6.0-SNAPSHOT`. Nenhum artefato foi publicado.

## Evidencias locais do 0.6

- `./mvnw clean verify` passou no OpenJDK 21.0.12 e no JBR OpenJDK 25.0.4,
  sempre com `--release 21`: 108 testes, zero falhas e zero skips.
- JaCoCo do core: 2.397/2.530 linhas (94,74%) e 1.490/1.860 branches (80,11%);
  gates 85%/80% atendidos sem exclusoes artificiais.
- O teste black-box depende diretamente apenas do agregador e cobre CSV/XLSX,
  review, plano confirmado, layout fast, extensoes, observer, fontes em memoria,
  dados invalidos e falhas tecnicas. O quickstart completo passou.
- A migracao para `io.github.felipemacedo1.rizoma.*` foi recompilada nas duas
  JVMs; JavaDoc/doclint passou e os JARs contem zero entradas do package antigo.
- Corpora preservados: 0.2 com 22/23 top-1, 23/23 top-3, 7/7 abstencoes e 5/5
  no-match; 0.4 com top-1 3/4, top-3 3/4 -> 4/4, um caso melhorado e um piorado.
- Smoke API com 100.000 linhas sob `-Xmx256m`: Workflow/Simple full
  5,127/4,648 s, dry run 1,054/0,646 s e fast+dry 0,609/0,587 s. A ordem
  favorece chamadas posteriores; nao e benchmark nem prova de superioridade.
- Volume com 1.000.000 linhas sob `-Xmx256m`: analyze 51,50 s, fast 1,02 s,
  adaptive 1,01 s e dry run 7,32 s; todas as contagens foram verificadas. A
  repeticao 0.5 mediu 50,21/1,06/1,08/7,32 s, sem regressao relevante observada.
- Repeticao posterior ao rename confirmou as mesmas contagens: 53,88/1,08/
  1,09/7,65 s para analyze/fast/adaptive/dry run.

## Proximo passo

Revisar conjuntamente o diff ainda nao consolidado de 0.5/0.6. Commit e push
somente quando solicitados; o remoto ainda esta no commit do 0.4 conforme
verificacao de 2026-09-12. Nao iniciar REST, UI, import real, ROI ou matching
global por inferencia. A pendencia operacional do CI remoto permanece
registrada sem bloquear trabalho local.
