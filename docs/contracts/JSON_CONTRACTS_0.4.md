# Contratos JSON do milestone 0.4

O schema de destino permanece 1.0. `DryRunResult` permanece 1.0. O 0.4 adiciona
ou evolui contratos experimentais sem mudar contratos antigos em silencio:

| Contrato | Versao produzida | Compatibilidade de leitura |
|---|---:|---|
| TargetSchema | 1.0 | 1.0 |
| AnalysisResult | 1.3 | `explain` aceita 1.0, 1.1, 1.2 e 1.3 |
| MappingPlan | 1.1 | Jackson aceita 1.0 sem campos de knowledge |
| DryRunResult | 1.0 | 1.0 |
| MappingFeedback | 1.0 | 1.0 |
| knowledge JSON Lines | 1.0 por evento | 1.0 |

Jackson continua configurado para rejeitar propriedades desconhecidas. Campos
ausentes das versoes anteriores recebem somente defaults de compatibilidade
documentados; nenhuma evidencia e inventada.

## AnalysisResult 1.3

Campos aditivos:

- `knowledgeSnapshotId`: identidade reproduzivel do snapshot; `NO_KNOWLEDGE`
  quando o default NoOp foi usado;
- `knowledgeVersion`: versao do algoritmo/formato do snapshot;
- `historicalEvidenceByColumn`: lista limitada aos pares historicos dos
  candidatos retidos no proprio relatorio para cada coluna.

O candidato possui um `ScoreComponent` de ID `history`. Sem evento compativel,
ele e indisponivel, tem contribuicao zero e motivo explicito. Com evento, a
explicacao inclui contagens, suporte suavizado, confiabilidade e contribuicao.

## MappingPlan 1.1

Adiciona `knowledgeSnapshotId` e `knowledgeVersion`. Esses campos fazem parte do
`planId`. Um documento 1.0 sem ambos e lido como `NO_KNOWLEDGE`/`1.0`; isso nao
faz o plano consultar uma knowledge base. O dry run nunca troca o snapshot nem
altera mappings.

## MappingFeedback 1.0

Exemplo sintetico; nao contem celula nem header original:

```json
{
  "formatVersion": "1.0",
  "feedbackId": "review-001",
  "timestamp": "2026-01-01T00:00:00Z",
  "type": "CORRECTED",
  "source": {
    "sourceColumnId": "c0",
    "position": 0,
    "originalHeaderLength": 10,
    "normalizedTokenCount": 2,
    "normalizedSourceName": "codigo cliente"
  },
  "scope": {
    "schemaId": "supplier",
    "schemaVersion": "1",
    "schemaFingerprint": "sha256...",
    "locale": "pt-BR",
    "domainContext": "supplier"
  },
  "suggestedTargetFieldId": "supplier.code",
  "humanTargetFieldId": "supplier.document",
  "previousScore": 0.61,
  "provenance": "human-review",
  "engineVersion": "0.4.0-SNAPSHOT",
  "configurationFingerprint": "sha256..."
}
```

Cada linha do arquivo e um objeto completo seguido de LF. Uma linha final sem
LF e tratada como escrita truncada, mesmo quando o prefixo pareca JSON valido.
IDs repetidos com evento identico sao idempotentes; o mesmo ID com conteudo
diferente torna o arquivo/evento invalido.

As regras de contexto, privacidade, score e snapshot estao em
[MAPPING_FEEDBACK_0.4.md](MAPPING_FEEDBACK_0.4.md).
