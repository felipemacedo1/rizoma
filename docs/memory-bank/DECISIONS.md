# Decisoes arquiteturais

## 2026-09-10 - Nucleo Python sem dependencia de runtime

O MVP usa a biblioteca padrao para CSV e inferencia lexical. Isso reduz o custo
de instalacao e permite validar o contrato antes de adicionar XLSX ou IA.

## 2026-09-10 - IA como fallback explicavel

Heuristicas deterministicas resolvem correspondencias claras. Um futuro
adaptador LLM atuara somente em ambiguidades, com minimizacao de dados e saida
estruturada, preservando reproducibilidade e privacidade.

## 2026-09-10 - Autonomia confinada ao workspace

O Codex usa aprovacao `never` com sandbox `workspace-write`. A configuracao
elimina interrupcoes rotineiras sem conceder acesso irrestrito a maquina.
