# DryRunResult 1.0

O `DryRunResult` JSON 1.0 e experimental. Ele e produzido pelo mesmo
`MappingEngine.dryRun` usado pela API e CLI, apos reabrir a fonte e validar o
plano. O metodo nao recebe `MigrationSink` ou qualquer porta de destino; logo o
dry run nao consegue escrever em banco/ERP por sua propria API.

Campos de identidade: formato, engine, plano, fonte/fingerprint,
schema/versao/fingerprint e configuration fingerprint. Contagens reais:
linhas processadas, validas, validas com warnings, invalidas e puladas;
celulas, celulas nao vazias, caracteres observados, campos mapeados,
transformacoes, validacoes e linhas que requerem revisao manual.

Erros e warnings possuem totais, mapas limitados por codigo, contagem de erros
por campo destino e exemplos limitados. Um `RowIssue` contem record number,
linha fisica, IDs de origem/destino/transformer/validator, codigo, motivo e
somente `<redacted:length=N>`. Nenhum valor original ou transformado e retido
no relatorio.

`terminatedEarly` e `terminationCode` distinguem conclusao normal,
`MAX_ERRORS_REACHED` e `FAIL_FAST_DATA_ERROR`. `durationMillis` e operacional e
nao integra a comparacao de determinismo semantico.

Garantias: leitura streaming, fonte read-only, fingerprint antes/depois,
recursos fechados, colecoes de erro limitadas, nenhuma rede iniciada pelo core e
nenhum destino. Nao garante constraints de banco, unique/FK, efeitos de uma
migracao futura, atualidade de dados externos nem aptidao para producao.
