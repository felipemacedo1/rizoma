# Roadmap do Rizoma

O roadmap detalhado e seus criterios de aceite ficam na
[especificacao tecnica](architecture/TECHNICAL_SPECIFICATION.md).

## Concluido: especificacao

- [x] Definir problema, casos de uso e requisitos.
- [x] Escolher Java 21 como baseline, com CI tambem em Java 25.
- [x] Escolher Maven 3.9.x via Maven Wrapper.
- [x] Definir monolito modular de biblioteca e CLI como primeiro adaptador.
- [x] Separar core de CSV, Excel e regras semanticas pt-BR.
- [x] Definir scoring explicavel, confidence conservadora e performance limitada.
- [x] Revisar contradicoes, riscos e complexidade desnecessaria.

## 0.1a: analise CSV explicavel (implementacao local concluida)

- [x] Maven Wrapper 3.9.16 e build multimodulo minimo.
- [x] Core com dominio e cursor de dataset de passagem unica.
- [x] Reader CSV streaming.
- [x] Normalizacao generica e pack pt-BR inicial.
- [x] Profiling limitado e tipos CPF/e-mail/telefone/data.
- [x] Dice e Levenshtein normalizado.
- [x] Score, ranking, confidence e explanation.
- [x] CLI `analyze` e `explain` com relatorio JSON.
- [x] Testes de unidade, propriedade, contrato, integracao e 1M de linhas.
- [x] `verify` local em Java 21 e Java 25 com `--release 21`.
- [ ] Confirmar a matriz 21/25 e o quickstart no GitHub Actions. A execucao
  `34589348866` foi criada, mas os jobs nao iniciaram devido ao bloqueio de
  cobranca da conta GitHub.

## 0.1b: Excel e seguranca (implementacao local concluida)

- [x] Adaptador XLSX streaming e XLS legado com limite proprio.
- [x] Deteccao por assinatura, selecao de planilha e identidade reproduzivel.
- [x] Datas, gaps, identificadores formatados e formulas sem avaliacao.
- [x] Limites de ZIP, expansao, entradas, planilhas e XLS em memoria.
- [x] Rejeicao de path traversal, macro e relacionamento externo.
- [x] Paridade top-1 CSV/XLS/XLSX para os sete campos da fixture principal.
- [x] API e CLI usam o mesmo pipeline para os tres formatos.
- [x] `verify` local em Java 21 e Java 25; suite ampliada pelo hardening para
  44 testes sem falha.
- [ ] Reexecutar GitHub Actions apos resolver o bloqueio de cobranca.
- [ ] Publicar release/tag/artefatos somente mediante autorizacao explicita.

O bloqueio de billing e uma pendencia operacional conhecida e nao bloqueia os
incrementos posteriores verificados localmente.

## 0.2: entendimento e qualidade de mapping

- [x] Jaccard, Jaro, Jaro-Winkler, trigrama e cosine com testes matematicos e
  propriedades de simetria/limites.
- [x] Subscore lexical correlacionado e modo baseline 0.1 para ablation.
- [x] Cardinalidade exata limitada e HyperLogLog `p=10` estimado.
- [x] Top-K Space-Saving, entropia, distribuicoes e Welford numerico.
- [x] Anomalias deterministicas com localizacoes protegidas e colecoes limitadas.
- [x] Semantic confidence separada de shape/validity/reliability.
- [x] Pruning explicavel somente para schemas grandes, preservando alias exato.
- [x] AnalysisResult JSON 1.2 e leitura retrocompativel 1.0/1.1.
- [x] Corpus sintetico: cinco datasets, 28 colunas, 23 mappings rotulados.
- [x] Baseline/ablation, matriz semantica e falhas preservadas.
- [x] Volume de 1 milhao sob `-Xmx256m`: 36,46 s, RSS 172.172 KiB na execucao final.
- [ ] JMH, adiado ate o subscore lexical estabilizar.

## 0.3: transformacao, validacao e dry run

- [x] `MappingPlan` 1.0 explicito, somente com mappings confirmados e ligado a
  fingerprints de fonte, schema, configuracao e registro de regras.
- [x] Transformers tipados JDK-only e canonicalizadores CPF/telefone/CEP pt-BR.
- [x] Datas e decimais conservadores com locale/formato explicito; zeros de
  identificadores TEXT preservados.
- [x] Validators required, regex, length, enum, CPF checksum e ranges.
- [x] Pipeline de linha com `VALID`, `VALID_WITH_WARNINGS`, `INVALID` e `SKIPPED`.
- [x] Policies `FAIL_FAST`, `SKIP_ROW`, `COLLECT_ERRORS` e colecoes limitadas.
- [x] `DryRunResult` 1.0 com contagens reais, motivos e amostras protegidas.
- [x] CLI `plan`/`dry-run` e API Java sem porta de destino.
- [x] Regressao do corpus 0.2 e gates de cobertura preservados.
- [x] Java 21/25, quickstart e volume 1M verificados e registrados.
- [x] Consolidar o milestone em commit/push quando solicitado.

## Versoes seguintes

- **0.2:** implementado, verificado e persistido no remoto.
- **0.3:** transformacao, validacao, plano ligado a fingerprints e dry run sem sink.
- **0.4:** feedback e knowledge base.
- **0.5:** matching global bipartido opt-in.
- **0.6:** SPI documentada e novas fontes orientadas por demanda.
- **0.9:** API candidate, hardening e benchmarks publicados.
- **1.0:** API estavel, release/SBOM e qualidade documentada.

## Hardening da candidata 0.1

- [x] Adicionar `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` e
  `CHANGELOG.md` coerentes com o estado real.
- [x] Documentar que fixtures sao sinteticas e auditar dados versionados.
- [x] Testar `explain` com relatorio JSON 1.0 sem `structure.attributes`.
- [x] Testar rejeicao de relacionamento externo XLSX e zero conexoes de rede.
- [x] Reexecutar `clean verify` local em Java 21 e Java 25.
- [x] Reexecutar quickstart e verificar mascaramento das saidas.
- [ ] Remover `SNAPSHOT` em commit de release separado.
- [ ] Obter CI remoto verde depois de resolver o bloqueio de billing.
- [ ] Criar tag/release somente mediante nova autorizacao.
