# Contratos JSON do incremento 0.2

O schema de destino permanece 1.0. O `AnalysisResult` produzido pelo motor 0.2
usa `formatVersion` 1.2 e estende, sem reinterpretar, os formatos 1.0 e 1.1. O
core continua independente de JSON; Jackson pertence ao adaptador CLI.

## AnalysisResult 1.2

A raiz acrescenta `prunedCandidatesByColumn`. Cada coluna possui uma lista
limitada de `{targetFieldId, reason}` para candidatos excluidos antes das
metricas caras em schemas com mais de 128 campos. Em schemas menores a lista e
vazia e todos os pares sao avaliados. `UNMAPPED` continua implicito na decisao
`NO_MATCH`; alias exato nunca e podado. O default retem no maximo 100
explicacoes por coluna; se houver mais exclusoes, `warnings` informa quantas
explicacoes foram retidas do total, sem materializar a matriz inteira.

`ColumnProfile` acrescenta `statistics`:

- `cardinality`: valor, `accuracy`, metodo e erro relativo esperado;
- `uniqueRatio`: cardinalidade dividida pelos valores nao vazios;
- `topValues`: ranking protegido, contagem, erro superior e precisao;
- `entropyBits`, `entropyAccuracy` e `entropyErrorBound`;
- `numericSummary`: contagem, media, variancia populacional, desvio, minimo e
  maximo por Welford;
- distribuicoes limitadas e exatas de comprimento e padrao;
- `mixedTypeRatio` e `nullRatio`;
- tipo semantico dominante, confidence index semantico, valid/invalid ratios;
- anomalias agregadas e localizacoes limitadas com valor mascarado.

Frequencias permanecem exatas enquanto houver ate 1.024 valores distintos.
Depois disso, cardinalidade usa HyperLogLog com `p=10`, 1.024 registradores e
erro relativo esperado de aproximadamente 3,25%; top-K usa Space-Saving com 10
contadores. Entropia exata usa a tabela limitada; apos a transicao, o resultado
e o centro de um intervalo entre limites inferior/superior e o semi-intervalo e
publicado em `entropyErrorBound`. Como a quantidade de categorias vem do HLL,
esse campo e uma faixa heuristica da estimativa, nao um intervalo estatistico
garantido. Toda aproximacao usa `accuracy=ESTIMATED`.

`SemanticEvidence` acrescenta `semanticConfidence = validityScore *
reliability`. Evidencia abaixo de 0,5 continua disponivel no mapa detalhado, mas
nao e promovida a tipo dominante.

## Compatibilidade

`explain` aceita 1.0, 1.1 e 1.2. Quando le 1.0/1.1, estatisticas e pruning
ausentes recebem representacao `UNAVAILABLE`/vazia, nunca valores inventados.
O teste de regressao remove fisicamente os campos 1.2 antes de desserializar os
dois formatos antigos. Propriedades desconhecidas continuam rejeitadas.

O formato 1.2 nao adiciona timestamp, duracao ou run ID. Fingerprints de fonte,
schema e configuracao continuam determinando reproducibilidade semantica.
