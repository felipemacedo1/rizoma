# MappingPlan 1.0

`MappingPlan` e o contrato experimental que liga confirmacoes explicitas de
mapeamento a uma analise concreta. O core o representa como objeto Java
imutavel; JSON e responsabilidade da CLI/Jackson. Propriedades JSON
desconhecidas sao rejeitadas.

O plano registra `sourceId` e SHA-256 da fonte, identidade e fingerprint do
schema, versao e fingerprint da configuracao, versao do motor, `planId`,
mappings, colunas de origem nao mapeadas e confirmacoes. Cada mapping referencia
uma coluna por `cN`, um campo destino e listas ordenadas de steps
`{id, version, options}` de transformacao e validacao. Um mapping com
`confirmed=false` e invalido.

`MappingPlanner.create` aceita somente selecoes explicitas. Ele deriva apenas
steps que existem no 0.3: conversoes pelo tipo fisico, canonicalizadores
pt-BR declarados pelo tipo semantico, `required`, regex de e-mail e checksum de
CPF. Regras adicionais podem ser expressas por `MappingPlan.Step` usando
`MappingPlanner.configure`, que devolve outro plano imutavel e recalcula o
`planId`; IDs e versoes desconhecidos falham antes de produzir resultado
enganoso.
Relatorios de analise produzidos por outra versao do engine exigem nova analise
e nao originam um plano 0.3 aparentemente utilizavel.

Antes do dry run, o engine recalcula os fingerprints. Mudanca de conteudo ou
identidade da fonte gera `INVALIDATE_PLAN`; mudanca de schema, configuracao,
registro de regras ou versao do motor gera `REQUIRE_REANALYSIS`. Campo destino
obrigatorio sem mapping gera `INCOMPLETE_PLAN`. O plano nao e autorizacao para
escrita e nao possui destino.

Exemplo gerado pela CLI:

```bash
java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  plan target/analysis.json --schema examples/dry-run-customer.schema.json \
  --map c0=customer.document --map c1=customer.email \
  --out target/mapping.json
```

`planId` e deterministico para a mesma analise, mappings, ordem normalizada de
mappings e configuracao dos steps. Ele e uma identidade reproduzivel, nao uma
assinatura criptografica nem prova de aprovacao por uma pessoa especifica.

## Evolucao compativel no 0.4

O formato produzido passou a 1.1 e acrescenta `knowledgeSnapshotId` e
`knowledgeVersion`. Ambos participam do `planId`, congelando a proveniencia
historica da analise. O plano nao consulta a knowledge base durante dry run e
nao muda com eventos posteriores. Documentos 1.0 sem esses campos continuam
desserializaveis com o default `NO_KNOWLEDGE`; os demais invariantes 1.0
permanecem. Consulte [JSON_CONTRACTS_0.4.md](JSON_CONTRACTS_0.4.md).
