# Changelog

Todas as mudancas relevantes do Rizoma serao registradas neste arquivo. O
formato segue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) e o
projeto pretende usar [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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

### Security

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
- Contratos JSON 1.0/1.1/1.2, arquitetura, roadmap e estado de release documentados.

### Tests

- Testes de pipeline, ambiguidade de data/decimal, zeros iniciais, policies,
  fingerprints, limites, API sem CLI e fluxo CSV/XLSX pela CLI.
- Regressao de `explain` para relatorio JSON 1.0 sem atributos introduzidos em
  1.1.
- Rejeicao de relacionamento externo XLSX com prova de zero conexoes.

### Known limitations

- O desenvolvimento esta em `0.3.0-SNAPSHOT`; nenhuma tag ou release foi
  publicada.
- Score e `confidenceIndex` sao heuristicas `UNCALIBRATED`, nao probabilidades.
- XLS legado e a tabela de shared strings XLSX nao possuem memoria O(1).
- Unique/foreign key, CNPJ, destino real e matching global nao estao
  implementados; dry run nao equivale a importacao nem prontidao de producao.
- O GitHub Actions permanece bloqueado por billing antes de executar os jobs.
- O corpus atual e pequeno, nao calibrado e mantem uma falha de documento
  empresarial sem detector CNPJ.
