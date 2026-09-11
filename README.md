# Rizoma

Motor extensivel para importar CSV e, nas proximas etapas, XLSX com deteccao de
formato, mapeamento inteligente de colunas, validacao e trilha de auditoria.

O MVP ja executa um fluxo completo para CSV:

1. detecta delimitador e codificacao;
2. compara cabecalhos com nomes e aliases do schema;
3. calcula confianca e impede que uma coluna de origem seja reutilizada;
4. bloqueia importacoes com campos obrigatorios nao mapeados;
5. gera CSV normalizado e relatorio JSON auditavel.

## Inicio rapido

Requer Python 3.11 ou superior. O nucleo nao possui dependencia de runtime.

```bash
python -m smart_import_engine.cli plan examples/customers.csv --schema examples/customer_schema.json
python -m smart_import_engine.cli run examples/customers.csv --schema examples/customer_schema.json --output output/customers.csv
```

Durante o desenvolvimento:

```bash
python -m unittest discover -s tests -v
python -m compileall -q src
```

Para instalar o comando `rizoma` e as ferramentas de desenvolvimento:

```bash
python -m venv .venv
.venv/bin/pip install -e '.[dev]'
rizoma --help
```

## Schema

```json
{
  "fields": {
    "customer_name": {
      "aliases": ["nome", "nome do cliente"],
      "required": true
    },
    "email": {
      "aliases": ["e-mail", "email principal"],
      "required": true
    }
  }
}
```

Mapeamentos abaixo do limiar sao tratados como nao resolvidos. O plano sempre
deve ser inspecionavel antes de evoluirmos para inferencia por LLM.

## Projeto orientado a agentes

- `AGENTS.md`: contrato operacional e criterio de conclusao.
- `.codex/config.toml`: autonomia sem prompts, limitada ao workspace.
- `docs/memory-bank/`: estado, decisoes e aprendizados versionados.
- `docs/architecture.md`: limites e desenho incremental.
- `docs/roadmap.md`: proximas entregas priorizadas.

Veja [.codex/README.md](.codex/README.md) antes de ampliar permissoes.
