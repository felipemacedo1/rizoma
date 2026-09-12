# Changelog

Todas as mudancas relevantes do Rizoma serao registradas neste arquivo. O
formato segue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) e o
projeto pretende usar [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Milestone 0.6: artefato agregador `rizoma`, fachada `Rizoma`,
  `ProcessRequest`/`ProcessResult`, status e rotas orientados ao consumidor.
- Factories reabriveis para `Path`, `byte[]` e `InputStream` limitado, builders
  ergonomicos de schema e hierarquia publica pequena de falhas tecnicas.
- Modulo black-box de adocao que depende diretamente apenas do agregador e
  compila exemplos simple, workflow, extension e layout conhecido.
- Milestone 0.5: layout signature/template, registry NoOp/InMemory, classificacao
  de drift e rotas full/fast/adaptive.
- `MappingPlan` 1.2 com projection sources source-column, constant, derived e
  unmapped, alem de source columns explicitamente ignoradas.
- Operacoes derivadas deterministicas CONCAT, COALESCE, ADD, SUBTRACT, MULTIPLY
  e DIVIDE com aritmetica `BigDecimal` e erros tipados.
- CLI `template create`, `recognize` e `explain-plan`; `plan` aceita constants,
  derived, ignored e unmapped target.
- Milestone 0.4: feedback humano explicito `CONFIRMED`, `REJECTED` e
  `CORRECTED`, agregado como evidencia historica deterministica.
- Knowledge bases NoOp e InMemory no core, mais adaptador JSON Lines limitado e
  estrito na CLI.
- `AnalysisResult` 1.3 e `MappingPlan` 1.1 com identidade/versionamento do
  snapshot de knowledge.
- CLI `feedback confirm/reject/correct` e opcao `analyze --knowledge`.
- Corpus separado de feedback com melhora, supressao, conflito e regressao
  deliberadamente preservada.
- Milestone 0.3: `MappingPlan` 1.0 ligado a fingerprints e confirmacoes
  explicitas, transformers/validators tipados e `DryRunResult` 1.0.
- Transformacoes String, Integer, Long, BigDecimal, LocalDate, Boolean e
  canonicalizacao pt-BR de CPF, telefone e CEP.
- Validacoes required, regex, length, enum, CPF checksum e ranges numerico/data.
- CLI `plan` e `dry-run`, com policies de erro e relatorios limitados e
  protegidos, sem qualquer sink de destino.
- Milestone 0.2: Jaccard, Jaro, Jaro-Winkler, trigramas e cosine agrupados em
  subscore lexical explicavel.
- Profiling limitado com cardinalidade exata/HLL, Space-Saving, entropia,
  distribuicoes, unique ratio e estatistica numerica incremental.
- Anomalias deterministicas com contagens e localizacoes protegidas.
- Corpus sintetico de cinco dominios, avaliacao, ablation e matriz semantica.
- Relatorio JSON 1.2 com estatisticas e candidatos podados explicados.
- API Java sem framework para analisar uma fonte tabular contra `TargetSchema`.
- CLI `analyze` e `explain` com relatorio JSON estrito e explicavel.
- Leitura CSV record-wise com deteccao limitada de delimitador e header.
- Leitura XLSX por worksheet em streaming e XLS legado sob limite de tamanho.
- Normalizacao de headers e aliases iniciais pt-BR.
- Profiling limitado, tipos fisicos e detectores de CPF, e-mail, telefone e data.
- Dice, Levenshtein normalizado, score composto, ranking, margem e abstencao.
- Contrato de resultado JSON 1.1 e leitura compativel de relatorios 1.0.
- Teste de volume CSV com um milhao de registros sob `-Xmx256m`.

### Changed

- Packages Java padronizados de `io.github.rizoma.*` para
  `io.github.felipemacedo1.rizoma.*`, alinhados ao namespace Maven controlado,
  antes de qualquer release ou publicacao.

### Security

- A Simple API mantem `AUTO_MAP` desligado, nunca confirma sugestoes, nao possui
  sink e protege mensagens tecnicas contra detalhes de adapters ou celulas.
- Fontes one-shot sao materializadas somente sob limite explicito e sem
  temporario oculto; o caller continua responsavel por fechar seu stream.
- Fast/adaptive path calcula SHA-256 do conteudo atual, usa guard limitado e
  escala drift perigoso para analise completa; nunca reutiliza o plano antigo.
- Layout fingerprint e relatorios de drift nao armazenam valores de celulas.
- Eventos de feedback nao persistem celulas nem header original; contexto e
  metadados possuem limites explicitos.
- Knowledge JSON Lines rejeita symlink, arquivo nao regular, truncamento,
  corrupcao, duplicata conflitante e limites excedidos; novos arquivos POSIX
  usam permissao `0600`.
- Dry run sem porta de destino, com revalidacao de fingerprints e rejeicao de
  plano incompleto para campos obrigatorios.
- Totais de erro separados de exemplos/mapas limitados; valores originais e
  transformados nao sao serializados.
- `AUTO_MAP` desabilitado por padrao no core.
- Amostras e mensagens publicas protegidas contra exposicao de celulas brutas.
- Formulas nunca avaliadas; politicas `cached`, `expression` e `reject`.
- Preflight XLSX para macros, relacionamentos externos, paths inseguros, limite
  de entradas, expansao e razao de compressao.
- DTD e entidades XML externas desabilitados.
- Fontes abertas somente para leitura e temporarios limitados e removidos.
- Teste de relacionamento externo XLSX confirma rejeicao antes de qualquer
  conexao de rede.

### Documentation

- Politicas de contribuicao, conduta e seguranca para a comunidade open source.
- Proveniencia sintetica das fixtures e orientacao para dados de teste.
- Contratos JSON de analise 1.0/1.1/1.2/1.3, plano 1.0/1.1 e feedback 1.0,
  arquitetura, roadmap e estado documentados.

### Tests

- Teste de consumidor externo cobre CSV/XLSX, review, plano confirmado,
  `FAST_REUSE`, extensoes, observer, fontes em memoria e separacao entre dados
  invalidos e falhas tecnicas.
- Rotas exact/reorder/rename/add/remove/type/semantic/duplicate, projection,
  divisao por zero, registry limitado e planos JSON 1.0/1.1 retrocompativeis.
- Contratos de confirmacao/rejeicao/correcao, isolamento, determinismo,
  snapshots, duplicatas, limites e conflito com semantica atual forte.
- Compatibilidade de `explain` com AnalysisResult 1.0/1.1/1.2 e leitura de
  MappingPlan 1.0 sem metadados de knowledge.
- Testes de pipeline, ambiguidade de data/decimal, zeros iniciais, policies,
  fingerprints, limites, API sem CLI e fluxo CSV/XLSX pela CLI.
- Regressao de `explain` para relatorio JSON 1.0 sem atributos introduzidos em
  1.1.
- Rejeicao de relacionamento externo XLSX com prova de zero conexoes.

### Known limitations

- O desenvolvimento esta em `0.6.0-SNAPSHOT`; nenhuma tag ou release foi
  publicada.
- A API de adocao e experimental antes de 1.0 e nao promete compatibilidade
  binaria; a composicao geral nao possui garantia de thread-safety concorrente.
- Score e `confidenceIndex` sao heuristicas `UNCALIBRATED`, nao probabilidades.
- XLS legado e a tabela de shared strings XLSX nao possuem memoria O(1).
- Unique/foreign key, CNPJ, destino real, registry persistente de layouts e
  matching global nao estao
  implementados; dry run nao equivale a importacao nem prontidao de producao.
- O GitHub Actions permanece bloqueado por billing antes de executar os jobs.
- O corpus atual e pequeno, nao calibrado e mantem uma falha de documento
  empresarial sem detector CNPJ.
- History usa nome normalizado exato, sem decay temporal, namespace
  organizacional dedicado, compactacao ou storage remoto; pode piorar casos
  ambiguos, como registrado no corpus 0.4.
