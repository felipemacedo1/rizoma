# Arquitetura

## Pipeline

```text
arquivo -> leitor -> amostra/cabecalhos -> mapeador -> validador -> transformador -> saida
                                      \-> plano e relatorio auditavel
```

O nucleo trabalha com linhas `dict[str, str]`. Leitores de CSV e XLSX convertem
seus formatos para esse contrato; assim, o mapeador e as regras de negocio nao
dependem da biblioteca usada para abrir planilhas.

## Modulos atuais

- `schema.py`: carrega e valida o contrato de destino.
- `csv_io.py`: detecta e le CSV, grava resultado normalizado.
- `mapping.py`: pontua aliases e resolve correspondencias um-para-um.
- `engine.py`: orquestra planejamento e execucao.
- `cli.py`: interface para pessoas e automacao.

## Principios

- Inferencia precisa ser explicavel: toda correspondencia inclui score e motivo.
- Campo obrigatorio ambiguo ou ausente interrompe a execucao antes da escrita.
- O plano e deterministico para os mesmos cabecalhos, schema e versao.
- LLM sera um adaptador opcional para ambiguidades, nunca requisito do nucleo.
- Persistencia futura deve ser idempotente e suportar dry-run e chave de deduplicacao.
