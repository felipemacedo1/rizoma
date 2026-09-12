# Feedback e knowledge base do milestone 0.4

Os contratos deste milestone representam feedback humano historico de forma
deterministica. Eles nao implementam machine learning e nao transformam
contagens em probabilidade.

## MappingFeedback 1.0

`MappingFeedback` e um evento imutavel. A gravacao e sempre explicita por
`MappingKnowledgeBase.record`; `analyze` e `dryRun` nunca a chamam. Os tipos sao:

- `CONFIRMED`: o alvo sugerido e o alvo humano sao iguais;
- `REJECTED`: o par sugerido recebe evidencia negativa e nao ha alvo humano;
- `CORRECTED`: o sugerido recebe evidencia negativa e o alvo correto recebe
  evidencia positiva.

O evento registra ID, instante ISO-8601 canonico em UTC, tipo, alvo sugerido,
alvo humano, score anterior quando o candidato estava retido, proveniencia,
versao do motor e fingerprint da configuracao. O escopo contem `schemaId`,
`schemaVersion`, fingerprint integral do schema, locale e contexto/dominio.

Os metadados da origem sao deliberadamente reduzidos a ID/posicao da coluna,
comprimento do header, quantidade de tokens e nome normalizado. O header
original e os valores das celulas nao sao persistidos. O nome normalizado ainda
pode ser sensivel se um produtor usar PII como header; quem registra feedback
deve tratar o arquivo de knowledge como dado interno protegido.

## Chave e isolamento

Cada lookup usa a chave exata:

```text
schemaId + schemaVersion + schemaFingerprint + targetFieldId
+ normalizedSourceName + locale + domainContext
```

Assim, `status` em outro schema, versao, locale ou dominio nao compartilha
evidencia silenciosamente. Um namespace organizacional dedicado permanece
planejado; no uso embarcado atual, bases de organizacoes diferentes devem usar
instancias/arquivos diferentes e contextos explicitos.

O nome e normalizado pela mesma regra da analise. Isso permite que `Cod Cli` e
`Cod. Cliente` produzam a mesma identidade no pack pt-BR. Nao existe busca
historica fuzzy no 0.4.

## Agregacao matematica

Para um par exato, sejam:

```text
P = CONFIRMED + CORRECTED_TO
N = REJECTED + CORRECTED_FROM
T = P + N
signedSupport = (P - N) / (T + 2)
support = min(1, ln(1 + T) / ln(1 + 8))
reliability = support * abs(signedSupport)
historicalScore = 1 se signedSupport > 0
                  0 se signedSupport < 0
                  0,5 se signedSupport = 0
```

O `+2` suaviza amostras pequenas e a saturacao logaritmica impede crescimento
ilimitado da confiabilidade. `historicalScore` representa somente direcao do
suporte; sua magnitude entra por `reliability`. No scorer, o componente possui
peso default 0,10. Uma rejeicao, portanto, acrescenta peso confiavel com valor
zero e reduz a media do candidato; uma confirmacao acrescenta contribuicao
positiva. Empate contraditorio tem confiabilidade zero.

Essa funcao e uma heuristica documentada, nao uma estimativa bayesiana nem
probabilidade calibrada. Recencia nao participa do calculo: os timestamps sao
retidos para auditoria, mas decay foi adiado para evitar dependencia silenciosa
do relogio.

## Snapshots e determinismo

`MappingKnowledgeBase.snapshot()` devolve uma visao imutavel com ID SHA-256,
versao e contagem de eventos. Um `AnalysisRequest` captura um snapshot uma unica
vez; para a mesma fonte, schema, configuracao e snapshot, o resultado semantico
e deterministico. `AnalysisResult` 1.3 registra o snapshot e os agregados
historicos usados por coluna.

`MappingPlan` 1.1 copia o ID e a versao do snapshot da analise para auditoria e
inclui ambos no calculo de `planId`. Eventos posteriores nao alteram um plano
existente. Para considerar conhecimento novo, e necessario analisar novamente e
criar outro plano. Dry run usa somente o plano confirmado, nao consulta knowledge
e nao aprende.

## Implementacoes

- `NoOpMappingKnowledgeBase`: default retrocompativel, snapshot vazio estavel;
  `record` falha para nao descartar feedback silenciosamente.
- `InMemoryMappingKnowledgeBase`: eventos limitados (100.000 por default),
  duplicata identica idempotente, duplicata conflitante rejeitada e snapshot
  indexado por `KnowledgeQuery`; lookup de candidato nao varre eventos.
- `JsonLinesMappingKnowledgeBase`: adaptador experimental da CLI. Formato
  append-only, estrito e versionado; defaults de 10 MiB, 100.000 eventos e 16
  KiB por linha. Locks cooperativos, `force`, permissao POSIX `0600` na criacao,
  rejeicao de symlink, truncamento, corrupcao e limites.

O adaptador de arquivo nao e um banco transacional multi-host. Nao ha
compactacao, reparo automatico, merge distribuido ou recencia no 0.4. JDBC,
Redis e servicos remotos nao foram criados.
