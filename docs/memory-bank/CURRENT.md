# Estado atual

Atualizado em: 2026-09-10

Projeto: Rizoma — uma estrutura simples na superficie e conectada em profundidade.

## Entregue

- MVP Python sem dependencias de runtime para CSV.
- Inferencia deterministica por aliases, similaridade e exclusividade de origem.
- CLI `plan` e `run`, relatorio JSON e bloqueio de campos obrigatorios ausentes.
- Workspace Codex autonomo, memoria nativa habilitada e regras em `AGENTS.md`.
- Repositorio privado `felipemacedo1/rizoma` criado via GitHub API e branch `main` publicada.

## Riscos conhecidos

- CSV com codificacoes fora de UTF-8/UTF-8-SIG/Latin-1 exige estrategia adicional.
- Similaridade lexical nao entende sinonimos ausentes do schema.
- XLSX ainda nao foi implementado.
- GitHub Actions encerra com `startup_failure` antes de criar jobs; o workflow esta ativo e o YAML foi validado, indicando bloqueio externo ao codigo. Os 7 testes locais passam.

## Proximo passo recomendado

Implementar o leitor XLSX opcional mantendo o contrato `dict[str, str]` e criar
fixtures cobrindo multiplas abas, celulas vazias e cabecalho deslocado.
