# Contratos JSON do incremento 0.1b

O schema de destino permanece na versao 1.0 descrita em
[`JSON_CONTRACTS_0.1A.md`](JSON_CONTRACTS_0.1A.md). O core continua trabalhando
somente com objetos Java; JSON e Jackson pertencem ao adaptador CLI.

## AnalysisResult 1.1

A versao 1.1 preserva todos os campos de `AnalysisResult` 1.0 e adiciona
`structure.attributes`, um mapa estrito de metadados seguros e especificos do
formato. Para CSV o mapa e vazio. Para XLS/XLSX ele registra:

- `sheetIndex`: indice zero-based da worksheet realmente analisada;
- `formulaPolicy`: `cached`, `expression` ou `reject`;
- `headerRow`: linha fisica one-based usada como header, quando existente;
- `dataStartRow`: primeira linha fisica elegivel para profiling.

O nome da planilha nao e persistido por padrao porque metadados de workbook
tambem podem conter informacao sensivel. O indice, combinado ao SHA-256 da
origem, identifica a tabela analisada de forma reproduzivel.

`configurationFingerprint` agora cobre a configuracao do engine, as
`readerOptions` em ordem canonica e a seed de amostragem. Assim, mudar planilha,
politica de formula ou limite especifico do reader muda a identidade da analise.

O comando `explain` aceita relatorios 1.0 e 1.1. Jackson continua configurado
para rejeitar propriedades desconhecidas. Em um relatorio 1.0, `attributes`
ausente e interpretado como mapa vazio; nenhum valor futuro e presumido.

## Opcoes Excel

Opcoes reconhecidas pelo `ExcelDataReader`:

| Opcao | Default | Semantica |
|---|---:|---|
| `header` | `detect` | `detect`, `first` ou `none` |
| `sheet` | unica planilha | nome exato ou indice zero-based; multiplas sem override causam abstencao estrutural |
| `formula` | `cached` | retorna cache, expressao ou rejeita; nunca avalia |
| `maxSheets` | `100` | maximo de worksheets |
| `maxPreambleRows` | `20` | maximo de linhas fisicas vazias antes do primeiro registro populado |
| `maxZipEntries` | `10000` | maximo de entradas OOXML |
| `maxExpandedBytes` | `536870912` | total expandido maximo |
| `maxZipEntryBytes` | `134217728` | tamanho expandido maximo por entrada |
| `minInflateRatio` | `0.01` | minimo de bytes compactados/expandidos |
| `maxLegacyBytes` | `20971520` | teto do modelo em memoria XLS, limitado tambem por `EngineLimits.maxBytes` |

Opcoes desconhecidas ou proprias de CSV sao rejeitadas; nao sao aceitas e
silenciosamente ignoradas. `sheet` prefere primeiro um nome exato, inclusive
quando o nome e numerico, e so depois interpreta o valor como indice.

Datas numericas com estilo de data sao emitidas em ISO local. Numeros usam o
formato de exibicao da celula, preservando zeros produzidos por formatos como
`000`. Linhas fisicas e gaps de celula sao preservados por posicao; linhas
fisicamente ausentes nao sao sintetizadas.
