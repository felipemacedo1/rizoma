# Estado atual

Atualizado em: 2026-09-11

## Confirmado

- Java 21 e o baseline, compilado com `--release 21`; Java 25 e a matriz de
  compatibilidade. Nenhum recurso preview e usado.
- Maven 3.9.16 esta fixado no Wrapper.
- A arquitetura e um monolito modular de biblioteca: `rizoma-core`,
  `rizoma-format-csv`, `rizoma-locale-ptbr` e `rizoma-cli` possuem codigo util e
  testes. O core depende apenas do JDK.
- Apache-2.0 e a licenca vigente; `NOTICE` preserva a atribuicao do projeto.
- O contrato JSON 1.0 esta documentado em `docs/contracts/JSON_CONTRACTS_0.1A.md`.

## Estado de implementacao

**IMPLEMENTADO E VERIFICADO LOCALMENTE:** incremento 0.1a. A API e a CLI leem
CSV em fluxo, detectam estrutura, normalizam headers, constroem perfis e
features limitadas, acumulam evidencia de CPF/e-mail/telefone/data, calculam
Dice e Levenshtein normalizado, ranqueiam candidatos, abstêm em casos
insuficientes/contraditorios, detectam colisao exclusiva e produzem relatorio
JSON protegido e explicacao.

**PLANEJADO / NAO IMPLEMENTADO:** XLS/XLSX (0.1b), detector completo de CNPJ e
CEP, profiling avancado, anomalias por linha, transformacao, validacao de
importacao, dry run, destinos, feedback, Hungarian, plugins dinamicos,
paralelismo, ML, embeddings e LLM.

## Evidencias locais

- `./mvnw verify` equivalente, usando cache Maven gravavel do sandbox, passou
  no OpenJDK 21.0.12: 29 testes, zero falhas.
- `clean verify` passou no Temurin 25.0.4.1 LTS e recompilou todas as fontes com
  `release 21`: 29 testes, zero falhas.
- JaCoCo do core: 455/475 linhas (95,79%) e 279/338 branches (82,54%); gates
  85%/80% atendidos sem exclusoes.
- Fixture principal: sete headers com o destino esperado em top-1. `Documento`
  com maioria de CPFs validos prioriza `customer.document`; onze digitos nus,
  empate, ausencia de evidencia e colisao nao geram `AUTO_MAP`.
- Volume: 1.000.000 de registros, 3 colunas, 50.000.034 bytes, Java 21,
  `-Xms32m -Xmx256m`, 25,61 s, RSS maximo medido 153.004 KiB, 1.000.000 linhas
  confirmadas no relatorio. RSS e memoria total do processo, nao heap.
- Quickstart `analyze` e `explain` executado localmente sobre `examples/`.

## Limites e riscos atuais

- A matriz GitHub Actions 21/25 e o quickstart estao configurados, mas nao foram
  executados remotamente porque codigo/CI ainda nao foram commitados nem
  enviados nesta entrega.
- Commons CSV entrega o campo depois de aloca-lo. O limite de bytes e antecipado,
  mas `maxFieldChars` e verificado apos tokenizacao; hardening anterior a
  alocacao permanece para 0.1b.
- Pesos sao baseline configuravel e a calibracao continua `UNCALIBRATED`.

## Proximo passo

Revisar o diff final e, quando houver autorizacao explicita, commit/push para
obter evidencia do GitHub Actions. Depois, iniciar 0.1b com XLS/XLSX e
hardening de arquivos, sem reabrir os contratos confirmados.
