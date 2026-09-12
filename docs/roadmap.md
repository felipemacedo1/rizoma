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
- [x] Consolidado e persistido no remoto no commit `0cc0a8c`.

## 0.4: feedback historico deterministico

- [x] `MappingFeedback` 1.0 imutavel para confirmacao, rejeicao e correcao.
- [x] Knowledge base NoOp default e InMemory limitada/indexada no core.
- [x] Adaptador experimental JSON Lines limitado e estrito na CLI.
- [x] Isolamento exato por schema/fingerprint, target, nome normalizado, locale
  e contexto.
- [x] Score/reliability historicos deterministas, explicados e com peso limitado.
- [x] Snapshot de knowledge no `AnalysisResult` 1.3 e `MappingPlan` 1.1.
- [x] CLI para feedback explicito e `analyze --knowledge`.
- [x] Corpus development/evaluation separado, incluindo melhora, supressao,
  conflito e caso deliberadamente piorado.
- [x] Regressao dos contratos de relatorio 1.0/1.1/1.2 e plano 1.0.
- [x] Consolidado administrativamente em commit/push quando solicitado.

## 0.5: Adaptive Layout & Data Projection

- [x] Fingerprints distintos para conteudo completo e estrutura protegida.
- [x] `LayoutTemplate` 1.0 imutavel, criado explicitamente de analise/plano
  compativeis, e registries NoOp/InMemory limitados e indexados.
- [x] Classificacao `EXACT`, `COMPATIBLE`, `DRIFTED`, `UNKNOWN` e rotas
  `FAST_REUSE`, `ADAPTIVE_REANALYSIS`, `FULL_ANALYSIS`.
- [x] Guard limitado de tipo/semantica, drift estruturado e fallback
  conservador diante de dependencia ausente, duplicidade ou contradicao.
- [x] Novo `MappingPlan` 1.2 vinculado ao conteudo atual; plano antigo nunca e
  reutilizado diretamente.
- [x] Projection sources source-column, constant, derived e unmapped; source
  columns ignoradas ficam separadas de no-match.
- [x] Operacoes derivadas declarativas CONCAT/COALESCE e aritmetica BigDecimal,
  incluindo erro tipado para divisao por zero.
- [x] CLI `template create`, `recognize`, `explain-plan` e opcoes de projection
  em `plan`; dry run usa o mesmo pipeline streaming.
- [x] Testes de fast/adaptive/full, reorder, drift, projection e leitura de
  MappingPlan 1.0/1.1.
- [ ] Registry persistente de layouts, adiado ate existir requisito operacional.

## 0.6: Public Java API & Adoption Layer

- [x] Artefato agregador `rizoma` com CSV/XLS/XLSX/core/pt-BR.
- [x] Fachada `Rizoma.create()` e builder avancado sem singleton global.
- [x] `ProcessRequest`, `ProcessResult`, status/rotas de alto nivel e observer
  opcional sem logging framework.
- [x] Comportamento conservador: sem recipe confirmado retorna
  `REVIEW_REQUIRED`; plano confirmado vai ao dry run; template usa as guardas
  FULL/FAST/ADAPTIVE existentes.
- [x] Builders ergonomicos de `TargetSchema`/`TargetField` e `Sources` para
  Path, byte array e InputStream limitado.
- [x] Hierarquia publica pequena para falhas tecnicas da Workflow API.
- [x] CLI composta por `Rizoma.create()`, sem registry manual paralelo.
- [x] Modulo externo nao publicavel com quatro exemplos de integracao, probe de
  overhead e testes de CSV, XLSX, review, plano, template, extensoes e erros.
- [x] Fronteiras Simple/Workflow/Extension/Internal e thread-safety documentadas.
- [x] Packages alinhados ao namespace controlado
  `io.github.felipemacedo1.rizoma.*` antes da primeira release.
- [ ] Compatibilidade binaria publica, reservada para 1.0.
- [ ] Publicacao do agregador, fora do escopo deste milestone.

## Versoes seguintes

- **0.2:** implementado, verificado e persistido no remoto.
- **0.3:** implementado, verificado e persistido no remoto.
- **0.4:** feedback e knowledge base implementados, verificados e persistidos.
- **0.5:** Adaptive Layout & Data Projection implementado localmente; verificacao
  e consolidacao administrativa registradas separadamente.
- **0.6:** Public Java API & Adoption Layer implementado e verificado localmente;
  consolidacao administrativa permanece separada.
- **0.9:** API candidate, hardening e benchmarks publicados.
- **1.0:** API estavel, release/SBOM e qualidade documentada.

Matching global/Hungarian ficou **DEFERRED / EVIDENCE-DRIVEN**. Projection
mapping suporta 1->0, 0->1, N->1 e 1->N, portanto assignment one-to-one nao e o
modelo central. O tema so retorna quando corpus ou casos reais demonstrarem
colisoes globais relevantes que nao sejam resolvidas por confirmacao/projecao.

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
