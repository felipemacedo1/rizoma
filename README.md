# Rizoma

Rizoma representa uma estrutura viva: simples na superficie, conectada e
complexa em profundidade.

Este repositorio e o ponto de partida de um motor inteligente e automatizado
para ingestao, profiling, mapeamento, validacao, transformacao e migracao de
dados. O foco inicial e CSV, XLS e XLSX. O projeto sera desenvolvido em **Java**.

A arquitetura inicial foi consolidada. Ainda nao ha codigo de producao: o
proximo passo e implementar o incremento vertical 0.1a, sem criar estruturas
vazias ou capacidades ficticias.

## Premissas iniciais

- Linguagem: Java.
- Baseline: Java 21, com testes tambem em Java 25.
- Build tool: Maven 3.9.x via Maven Wrapper.
- Entradas da versao 0.1: CSV, XLS e XLSX.
- Capacidades esperadas: deteccao, mapeamento inteligente, validacao,
  transformacao, importacao e auditoria.
- O core e deterministico e funciona sem LLM, rede, Spring ou banco.

## Documentacao

- [Especificacao tecnica e arquitetura](docs/architecture/TECHNICAL_SPECIFICATION.md)
- [Roadmap](docs/roadmap.md)
- [Estado atual](docs/memory-bank/CURRENT.md)
- [Decisoes arquiteturais](docs/memory-bank/DECISIONS.md)

As instrucoes operacionais ficam em `AGENTS.md`, a configuracao do Codex em
`.codex/config.toml` e a continuidade do trabalho em `docs/memory-bank/`.
