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

## 0.1a: analise CSV explicavel

- [ ] Maven Wrapper e build multimodulo minimo.
- [ ] Core com dominio e cursor de dataset.
- [ ] Reader CSV streaming.
- [ ] Normalizacao generica e pack pt-BR inicial.
- [ ] Profiling limitado e tipos CPF/e-mail/telefone/data.
- [ ] Dice e Levenshtein normalizado.
- [ ] Score, ranking, confidence e explanation.
- [ ] CLI `analyze` e `explain` com relatorio JSON.
- [ ] Testes de unidade, propriedade, integracao e 1M de linhas.

## Versoes seguintes

- **0.1b:** XLS/XLSX e seguranca de arquivos; publica 0.1.
- **0.2:** metricas restantes, profiling avancado, anomalias e corpus.
- **0.3:** transformacao, validacao, dry run e sink seguro de referencia.
- **0.4:** feedback e knowledge base.
- **0.5:** matching global bipartido opt-in.
- **0.6:** SPI documentada e novas fontes orientadas por demanda.
- **0.9:** API candidate, hardening e benchmarks publicados.
- **1.0:** API estavel, release/SBOM e qualidade documentada.
