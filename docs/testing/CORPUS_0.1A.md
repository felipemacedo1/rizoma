# Corpus sintético rotulado 0.1a

O corpus inicial e pequeno e serve para regressao funcional, nao para calibrar
probabilidades. Os pesos baseline foram preservados; os casos ambiguos esperam
revisao/abstencao em vez de forcar acerto. A fixture publica principal esta em
`examples/`; casos hostis menores sao construidos deterministicamente nos
testes para evitar arquivos redundantes.

| Caso rotulado | Resultado esperado | Prova automatizada |
|---|---|---|
| sete headers do exemplo, com acentos e aliases | sete destinos corretos em top-1 | `RizomaCliTest.mainFixture...` |
| `Documento`, 90% CPF valido | documento acima de telefone | `RizomaCliTest.documentContent...` |
| onze digitos nus | telefone ambiguo; nunca auto-map | `RizomaCliTest.documentContent...` |
| `dtNasc`, camelCase, snake_case e diacriticos | forma `data nascimento`, idempotente | `SimilarityAndNormalizationTest` |
| data `01/02/2026` | alternativa DMY/MDY registrada como ambigua | `ContractsTest.coreDetectors...` |
| identificador `00123` | tipo TEXT, zero preservado | `MappingEngineTest.profilesAll...` |
| coluna vazia | ausencia de evidencia e `NO_MATCH` | `MappingEngineTest.emptyEvidence...` |
| header enganoso com identidade semantica forte | candidato incompativel inelegivel | `MappingEngineTest.unconfigured...` |
| dois headers `Nome` | IDs `c0`/`c1`; explain por header falha | `CsvDataReaderTest` e `RizomaCliTest` |
| dois candidatos equivalentes | desempate deterministico, margem baixa | `MappingEngineTest.emptyEvidence...` |
| duas origens para destino exclusivo | conflito e revisao | `MappingEngineTest.equalCandidates...` |
| nenhuma correspondencia adequada | abstencao, sem NaN/infinito | `MappingEngineTest.emptyEvidence...` |
| CPF valido/invalido misturado | shape e checksum separados | `PtBrDetectorsTest` e `RizomaCliTest` |
| CSV irregular, limite excedido e cancelamento | erro codificado, seguro e recurso fechado | `CsvDataReaderTest` e `MappingEngineTest` |

Os 2.000 pares pseudoaleatorios do teste de Levenshtein verificam simetria,
faixa `[0,1]` e ausencia de NaN/infinito. Isso e um teste de propriedade com
seed fixa, nao um corpus estatistico nem benchmark.
