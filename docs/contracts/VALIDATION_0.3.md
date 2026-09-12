# Validacao 0.3

`Validator<T>` devolve `ValidationResult` com `PASS`, `WARNING` ou `FAILURE`,
ID/versao, codigo estavel e motivo. `ValidationContext` informa campo destino,
locale, representacao original transitoria e opcoes. Excecoes ficam reservadas
para configuracao/programacao invalida; dados rejeitados nao usam excecao como
fluxo normal.

Validadores implementados: `core:required`, `core:regex`, `core:length`,
`core:enum`, `core:numeric-range`, `core:date-range` e
`br:cpf-checksum`. Bounds numericos usam `BigDecimal`; bounds de data usam
`LocalDate` ISO. Regex/enum/ranges adicionais sao configurados como steps por
`MappingPlanner.configure`. O planner padrao aplica somente regras inferiveis
do contrato atual do schema: required, e-mail e CPF.

`FAIL_FAST` encerra depois da primeira linha com erro. `SKIP_ROW` continua e
contabiliza a linha como `SKIPPED`. `COLLECT_ERRORS` continua e contabiliza
`INVALID`. Todas respeitam `maxErrors`. O total continua exato ate a parada,
mas exemplos, codigos distintos e campos com erro usam limites configurados.
Warnings nao viram failures e produzem `VALID_WITH_WARNINGS` quando nao existe
erro na linha.

Unique e foreign key permanecem `PLANEJADO / NAO IMPLEMENTADO`: nao ha porta,
consulta externa ou `Set` ilimitado escondido nesta entrega.

O plano e suas regexes sao configuracao confiavel no 0.3. A implementacao usa
`java.util.regex.Pattern`; uma expressao hostil pode ter custo patologico dentro
de uma linha, e o limite de duracao so e verificado entre linhas. Sandbox ou
timeout isolado para regex nao sao anunciados nesta versao.
