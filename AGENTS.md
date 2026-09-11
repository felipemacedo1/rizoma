# AGENTS.md

## Missao

Projetar, implementar, testar e entregar um motor confiavel de importacao de
CSV/XLSX com mapeamento inteligente, validacao explicavel e operacao auditavel.

## Forma de trabalho

- Continue autonomamente enquanto existir uma proxima acao segura e objetiva.
- Prefira a menor arquitetura que resolva o requisito atual.
- Leia `docs/memory-bank/CURRENT.md` e `docs/architecture.md` antes de mudancas relevantes.
- Investigue o codigo e os testes antes de perguntar algo que possa ser descoberto localmente.
- Mantenha funcoes pequenas, tipadas, deterministicas e faceis de testar.
- Separe leitura, inferencia de mapeamento, transformacao, validacao e persistencia.
- Nao introduza LLM no caminho critico enquanto heuristicas locais resolverem o caso.
- Nunca envie conteudo importado, credenciais ou dados pessoais a servicos externos sem autorizacao explicita.
- Nao use comandos destrutivos, force-push nem reescreva historico compartilhado.
- Commits e push para o remoto configurado fazem parte da entrega quando forem solicitados.

## Criterio de conclusao

Uma mudanca so esta pronta quando:

1. comportamento e contratos estao implementados;
2. testes relevantes passam;
3. `python -m compileall -q src` passa;
4. documentacao afetada foi atualizada;
5. `docs/memory-bank/CURRENT.md` registra estado e proximo passo;
6. `docs/memory-bank/DECISIONS.md` ou `LEARNINGS.md` registra apenas conhecimento duravel novo;
7. `git diff --check` nao aponta problemas.

## Validacao padrao

```bash
python -m unittest discover -s tests -v
python -m compileall -q src
git diff --check
```

Dependencias opcionais nao devem impedir os testes do nucleo. Para mudancas de
formato, inclua fixtures pequenas e anonimizadas. Nunca versione planilhas reais
ou segredos.

## Memory bank

- `CURRENT.md`: estado presente, riscos e proximo passo. Atualize a cada entrega material.
- `DECISIONS.md`: decisoes arquiteturais duraveis, com data e motivo.
- `LEARNINGS.md`: falhas, comandos e fatos reutilizaveis confirmados.
- Nao grave tokens, senhas, dados pessoais, transcricoes ou suposicoes.
- Corrija entradas obsoletas em vez de apenas acumular contradicoes.
