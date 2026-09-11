# Contratos JSON do incremento 0.1a

Este documento descreve o adaptador JSON. O `rizoma-core` trabalha apenas com
objetos Java e nao depende de Jackson.

## TargetSchema 1.0

Exemplo executavel: [`examples/customer.schema.json`](../../examples/customer.schema.json).

```json
{
  "formatVersion": "1.0",
  "schemaId": "customer",
  "schemaVersion": "1.0",
  "context": "customer onboarding",
  "locale": "pt-BR",
  "fields": [
    {
      "id": "customer.document",
      "displayName": "CPF",
      "aliases": ["Documento"],
      "physicalType": "TEXT",
      "semanticTypes": ["br:cpf", "br:cnpj"],
      "required": true,
      "exclusive": true
    }
  ]
}
```

`formatVersion`, `schemaId`, `schemaVersion` e `fields` sao obrigatorios.
`context` e `locale` podem ser omitidos. Em cada campo, `id`, `displayName` e
`physicalType` sao obrigatorios; `aliases` e `semanticTypes` podem ser omitidos
ou vazios; `required` assume o valor JSON padrao `false`; `exclusive` assume
`true`. Tipos fisicos aceitos: `EMPTY`, `BOOLEAN`, `INTEGER`, `DECIMAL`, `DATE`,
`TEXT` e `MIXED`. Tipos semanticos sao IDs abertos e namespaced, como `br:cpf`.

Esses metadados servem somente para sugerir mapping no 0.1a. `required` e
`exclusive` sao preservados; apenas `exclusive` participa da deteccao de
colisoes. O motor nao executa validacao de obrigatoriedade nem importacao nesta
fase. Propriedades desconhecidas sao rejeitadas, nunca aceitas e ignoradas.

## AnalysisResult 1.0

O relatorio raiz contem:

- `formatVersion`, `engineVersion` e `calibration`;
- identidade e SHA-256 de origem, schema e configuracao;
- `structure`, com formato, charset, delimitador, header e colunas por posicao;
- `rowsProcessed`, `profiles`, `candidatesByColumn` e `decisionsByColumn`;
- `unmatchedColumns`, `conflicts`, `warnings` e `errors` seguros.

Cada componente de score inclui `available`, `value`, `weight`, `reliability`,
`contribution`, evidencia, motivo de indisponibilidade e limitacoes. Evidencia
indisponivel usa `available=false`, `value=null`, confiabilidade/contribuicao
zero e motivo textual. Medidas de perfil ainda inexistentes nao recebem zero
ficticio: simplesmente nao pertencem ao contrato 1.0 ou sao declaradas
indisponiveis no componente correspondente.

O relatorio 1.0 nao inclui timestamp, run ID ou duracao. Se forem adicionados em
versao futura, serao campos operacionais fora da comparacao de determinismo
semantico. O consumidor deve verificar `formatVersion`; o comando `explain`
rejeita versoes e propriedades desconhecidas.

Colunas usam identidade posicional (`c0`, `c1`...) separada do header. Assim,
dois campos chamados `Nome` continuam distintos e uma selecao apenas por header
duplicado e rejeitada como ambigua.

Os fingerprints vinculam o resultado ao conteudo, schema e configuracao
analisados. Na fase 0.3, qualquer plano aprovado devera verificar essas
identidades e ser invalidado se alguma delas mudar; o 0.1a nao executa planos.
