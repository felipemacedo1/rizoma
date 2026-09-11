# Changelog

Todas as mudancas relevantes do Rizoma serao registradas neste arquivo. O
formato segue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) e o
projeto pretende usar [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
- Contratos JSON 1.0/1.1, arquitetura, roadmap e estado de release documentados.

### Tests

- Regressao de `explain` para relatorio JSON 1.0 sem atributos introduzidos em
  1.1.
- Rejeicao de relacionamento externo XLSX com prova de zero conexoes.

### Known limitations

- A versao ainda e `0.1.0-SNAPSHOT`; nenhuma tag ou release foi publicada.
- Score e `confidenceIndex` sao heuristicas `UNCALIBRATED`, nao probabilidades.
- XLS legado e a tabela de shared strings XLSX nao possuem memoria O(1).
- Transformacao, validacao de importacao, dry run, destino e matching global nao
  estao implementados.
- O GitHub Actions permanece bloqueado por billing antes de executar os jobs.
