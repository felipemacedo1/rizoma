# Contratos JSON do milestone 0.3

O 0.3 nao altera o schema JSON 1.0 nem o `AnalysisResult` 1.2. `explain`
continua lendo AnalysisResult 1.0, 1.1 e 1.2. Dois contratos experimentais
independentes passam a existir:

- `MappingPlan` com `formatVersion: "1.0"`;
- `DryRunResult` com `formatVersion: "1.0"`.

Os campos e invariantes estao documentados em
[MAPPING_PLAN_0.3.md](MAPPING_PLAN_0.3.md) e
[DRY_RUN_RESULT_0.3.md](DRY_RUN_RESULT_0.3.md). A desserializacao da CLI rejeita
propriedades desconhecidas. Evidencia indisponivel continua representada no
AnalysisResult como componente indisponivel/null; os novos contratos nao
inventam score, probabilidade ou evidencia.

MappingPlan e DryRunResult 1.0 sao experimentais durante o snapshot 0.3.0 e
podem receber uma nova `formatVersion` antes de uma API estavel. Uma mudanca de
estrutura nunca sera aceita silenciosamente sob a mesma versao.
