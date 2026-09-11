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
- [x] `verify` local em Java 21 e Java 25, 42 testes sem falha.
- [ ] Reexecutar GitHub Actions apos resolver o bloqueio de cobranca.
- [ ] Publicar release/tag/artefatos somente mediante autorizacao explicita.

## Versoes seguintes

- **0.2:** metricas restantes, profiling avancado, anomalias e corpus.
- **0.3:** transformacao, validacao, dry run e sink seguro de referencia.
- **0.4:** feedback e knowledge base.
- **0.5:** matching global bipartido opt-in.
- **0.6:** SPI documentada e novas fontes orientadas por demanda.
- **0.9:** API candidate, hardening e benchmarks publicados.
- **1.0:** API estavel, release/SBOM e qualidade documentada.
