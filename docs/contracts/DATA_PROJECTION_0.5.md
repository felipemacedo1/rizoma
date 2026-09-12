# Data Projection 0.5

Projection define de onde vem o valor de cada campo destino. Ela substitui a
hipotese incorreta de que todo mapping e estritamente uma coluna para um campo,
sem introduzir scripting ou execucao arbitraria.

## Origens de valor

- `SOURCE_COLUMN`: referencia o ID posicional/estrutural (`c0`, `c1`...), nao
  apenas o texto do header.
- `CONSTANT`: texto explicito que passa pelos transformers e validators do
  target. O valor participa do `planId` e nao depende de estado oculto.
- `DERIVED`: operacao declarativa sobre source columns e/ou constantes.
- `UNMAPPED`: target deliberadamente sem origem. Campo required sem valor
  continua tornando o plano inexequivel no dry run.

Coluna fonte `IGNORED_BY_PLAN` nao e uma projection: seu ID aparece em
`ignoredSourceColumns`. `NO_MATCH_FOUND` permanece em
`unmappedSourceColumns`. Assim, uma decisao humana de nao usar uma coluna nao e
confundida com ausencia de candidato.

## Operacoes derived

| Operacao | Operand count | Semantica |
|---|---:|---|
| CONCAT | 1..32 | concatena na ordem declarada |
| COALESCE | 1..32 | primeiro texto nao vazio |
| ADD | 1..32 | soma `BigDecimal` |
| SUBTRACT | 2 | primeiro menos segundo |
| MULTIPLY | 1..32 | produto `BigDecimal` |
| DIVIDE | 2 | divisao DECIMAL128 |

Operandos sao somente `SOURCE_COLUMN` ou `CONSTANT`. Targets nao referenciam
outros targets, portanto nao existe DAG, ordenacao topologica ou ciclo no 0.5.
Nao existe JavaScript, SpEL, reflection, eval, DSL aberta ou codigo dinamico.

Numeros usam `BigDecimal`. `pt-BR` aceita, por exemplo, `1.234,56`; `en-US`
aceita `1,234.56`. Sem locale, separador de milhar/decimal ambiguo falha. IDs
TEXT nao sao convertidos por conter digitos. Erros normais de dado sao
resultados tipados:

- `DERIVED_INVALID_NUMBER`;
- `DERIVED_DIVISION_BY_ZERO`;
- `DERIVED_ARITHMETIC_ERROR`;
- `UNMAPPED_TARGET`.

O resultado transitorio da projection segue para os transformers/validators do
binding. Erro derived produz field result invalido com target, linha, codigo e
motivo seguros. Relatorios e logs nao publicam operandos de celula nem valor
produzido. `explain-plan` mostra operacao e IDs de origem; constantes sao
mascaradas por comprimento.

## Exemplo Java

```java
MappingPlan plan = new MappingPlanner().createProjected(analysis, schema,
    List.of(
        new MappingPlanner.ProjectionSelection("order.quantity",
            ProjectionSource.sourceColumn("c1"), "confirmed"),
        new MappingPlanner.ProjectionSelection("order.total",
            ProjectionSource.derived(new ProjectionSource.DerivedExpression(
                ProjectionSource.Operation.MULTIPLY,
                List.of(ProjectionSource.Operand.sourceColumn("c1"),
                        ProjectionSource.Operand.sourceColumn("c2")))), "confirmed"),
        new MappingPlanner.ProjectionSelection("order.status",
            ProjectionSource.constant("ACTIVE"), "confirmed")),
    List.of("c3", "c4"));
```

O mesmo source pode alimentar varios targets e varias colunas podem alimentar
um target derived. O dry run permanece streaming: por linha, cria apenas o mapa
de IDs para valores transitorios e resultados limitados pelos controles 0.3.

## Limites

Constantes/operandos tem limite de 16 KiB e nao aceitam quebras de linha; source
IDs tem limite de 128 caracteres. Expressoes possuem no maximo 32 operandos.
Planos e templates sao configuracao confiavel, nao dados nao confiaveis para
execucao arbitraria. Constant/default sao um unico conceito no 0.5 porque nao
ha semantica de aplicar default apenas diante de null; COALESCE expressa a
escolha explicitamente.
