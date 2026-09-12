# Contratos JSON de layout 0.5

Os contratos abaixo sao experimentais. O core trabalha com objetos Java e nao
depende de Jackson; parsing/serializacao estritos pertencem ao adaptador CLI.
Propriedades desconhecidas sao rejeitadas. Evidencia indisponivel usa lista
vazia, string vazia ou `null` conforme o tipo do campo, nunca numero inventado.

## Versoes produzidas

| Documento | `formatVersion` | Observacao |
|---|---:|---|
| AnalysisResult | 1.3 | inalterado no 0.5 |
| MappingPlan | 1.2 | adiciona projection e identidade de template/layout |
| LayoutSignature | 1.0 | estrutura protegida, nao conteudo |
| LayoutTemplate | 1.0 | receita reutilizavel confirmada |
| LayoutCompatibilityReport | 1.0 | drift e rota recomendada |
| LayoutRecognitionResult | 1.0 | decisao auditavel e plano atual opcional |
| DryRunResult | 1.0 | inalterado |

MappingPlan 1.0 e 1.1 continuam desserializaveis: na ausencia de
`projectionSource`, o binding e interpretado como `SOURCE_COLUMN` usando o
`sourceColumnId` historico. Campos de layout ausentes recebem string vazia e
rota `FULL_ANALYSIS`. Essa compatibilidade nao autoriza transformar um plano
antigo em template sem nova analise: `LayoutTemplate.create` exige plano 1.2.

## Fingerprints

- `sourceContentFingerprint`: SHA-256 dos bytes do arquivo atual. Identifica o
  conteudo e continua obrigatorio no plano usado por dry run.
- `LayoutSignature.fingerprint`: SHA-256 canonico de formato, charset,
  delimitador, presenca de header, atributos estruturais do reader, headers
  normalizados com posicao/ocorrencia e tipo/semantica observados. Nao inclui
  celulas, amostras nem frequencias.

O fingerprint de layout pode mudar por data drift observado na amostra. Ele e
uma signature de compatibilidade explicavel, nao hash criptografico do schema
externo nem garantia de que todos os valores futuros sigam o mesmo dominio.

## LayoutTemplate 1.0

Campos principais:

```json
{
  "formatVersion": "1.0",
  "templateId": "sha256-canonico",
  "templateVersion": "1",
  "name": "orders-import",
  "expectedLayout": {"formatVersion": "1.0", "fingerprint": "..."},
  "targetSchemaId": "orders",
  "targetSchemaVersion": "1",
  "targetSchemaFingerprint": "...",
  "configurationVersion": "0.5-default",
  "configurationFingerprint": "...",
  "knowledgeSnapshotId": "NO_KNOWLEDGE",
  "knowledgeVersion": "1.0",
  "originPlanId": "...",
  "bindings": [],
  "ignoredSourceColumns": ["c3", "c4"],
  "createdAt": "2026-09-12T00:00:00Z",
  "provenance": "explicit human confirmation",
  "engineVersion": "0.5.0-SNAPSHOT"
}
```

Todo source column esperado deve ser referenciado por ao menos um binding ou
estar em `ignoredSourceColumns`. IDs alvo nao se repetem. O template armazena
constantes quando configuradas; por isso o arquivo e configuracao confiavel e
deve receber a mesma protecao de acesso do plano. Nenhuma celula e copiada.

## LayoutRecognitionResult 1.0

`route` e um enum, nao probabilidade:

- `FAST_REUSE`: bindings estruturais foram resolvidos sem candidate generation;
- `ADAPTIVE_REANALYSIS`: somente a parte afetada recebeu checagem limitada;
- `FULL_ANALYSIS`: nenhuma reutilizacao segura; `mappingPlan` e `null`.

`classification` usa `EXACT`, `COMPATIBLE`, `DRIFTED` ou `UNKNOWN`.
`compatibility.drift` possui itens tipados como `ADDED_COLUMN`,
`REMOVED_COLUMN`, `RENAMED_OR_UNKNOWN_COLUMN`, `REORDERED_COLUMN`,
`TYPE_DRIFT`, `SEMANTIC_DRIFT`, `DUPLICATE_HEADER`,
`REQUIRED_SOURCE_MISSING`, `TARGET_SCHEMA_CHANGED`,
`STRUCTURE_METADATA_CHANGED`, `CONFIGURATION_CHANGED` e
`TEMPLATE_VERSION_UNSUPPORTED`.

Os contadores `rowsReadForRecognition`, `candidatePairsEvaluated` e
`similarityMetricsExecuted`, junto a `fullProfilingExecuted` e
`inferenceSkipped`, tornam o trabalho evitado auditavel. Um resultado fast ou
adaptive inclui um novo `mappingPlan` com `sourceFingerprint` do arquivo atual,
`layoutTemplateId`, `layoutTemplateVersion`, `layoutFingerprint` e
`executionRoute`.

## Registry e persistencia

`NoOpLayoutRegistry` e o default retrocompativel. `InMemoryLayoutRegistry` tem
limite default de 10.000 templates e indices por fingerprint de schema +
formato e por multiset estrutural de headers + formato. O segundo indice deixa
um template estruturalmente candidato expor `TARGET_SCHEMA_CHANGED`, sem scan
global. Nao existe File/JDBC/remote layout registry no 0.5. A CLI aceita um
arquivo `LayoutTemplate` explicito e o registra apenas em memoria para aquela
chamada.

## Determinismo e campos operacionais

Mesmos bytes, schema, opcoes, template e guard produzem a mesma decisao
semantica. `createdAt` e provenance de auditoria do template; duracoes externas
nao fazem parte da decisao. Mudanca de template, schema, configuracao ou
conteudo e visivel nos respectivos IDs/fingerprints.
