# Transformacoes 0.3

`ValueTransformer<S,T>` recebe valor e contexto tipados e devolve
`TransformationResult<T>`. Erro de dado usa `FAILURE`, nao excecao. O resultado
transitorio preserva `originalValue`, valor transformado quando disponivel,
status, codigo, mensagem, flags `lossy`/`ambiguous`, ID e versao da regra. O
relatorio publico nunca serializa esses valores.

`TransformationPipeline` executa steps ordenados e para na primeira falha.
Transformer inexistente, versao incorreta ou cadeia de tipos incoerente e erro
de configuracao do plano, nao erro de uma celula.

Implementacoes verificadas:

| ID | Entrada | Saida | Regra conservadora |
|---|---|---|---|
| `core:string-normalize` | String | String | trim/espaços e caixa configurada; mudanca gera warning com `lossy=true` |
| `core:integer` | String | Integer | range exato; zero inicial exige permissao explicita |
| `core:long` | String | Long | range exato; zero inicial exige permissao explicita |
| `core:big-decimal` | String | BigDecimal | separadores exigem `pt-BR` ou `en-US` explicito |
| `core:local-date` | String | LocalDate | ISO sem configuracao; formatos ordenados configuraveis; multiplas interpretacoes falham como ambiguas |
| `core:boolean` | String | Boolean | `true/false`; `sim/nao` ou `yes/no` apenas com locale correspondente |
| `br:cpf-canonical` | String | String | somente digitos e pontuacao esperada; 11 digitos |
| `br:phone-canonical` | String | String | 10/11 digitos nacionais; DDI opcional 55 |
| `br:cep-canonical` | String | String | somente digitos/hifen/espaço; 8 digitos |

Identificadores TEXT nao sao convertidos por parecerem numericos; `00123`
permanece `00123`. CNPJ nao possui transformer no 0.3, pois letras podem ser
significativas e o contrato oficial completo ainda nao foi implementado.
