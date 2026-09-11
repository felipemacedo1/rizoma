# Estado atual

Atualizado em: 2026-09-11

## Confirmado

- Java 21 e o baseline, compilado com `--release 21`; Java 25 e a matriz de
  compatibilidade. Nenhum recurso preview e usado.
- Maven 3.9.16 esta fixado no Wrapper.
- A arquitetura e um monolito modular de biblioteca: `rizoma-core`,
  `rizoma-format-csv`, `rizoma-format-excel`, `rizoma-locale-ptbr` e
  `rizoma-cli` possuem codigo util e testes. O core depende apenas do JDK.
- Apache-2.0 e a licenca vigente; `NOTICE` preserva a atribuicao do projeto.
- Os contratos JSON 1.0 e 1.1 estao documentados em
  `docs/contracts/JSON_CONTRACTS_0.1A.md` e
  `docs/contracts/JSON_CONTRACTS_0.1B.md`; o 0.1b produz 1.1 e a CLI `explain`
  continua aceitando relatorios 1.0.

## Estado de implementacao

**IMPLEMENTADO, VERIFICADO LOCALMENTE E PERSISTIDO NO REMOTO:** incrementos
0.1a e 0.1b. A API e a CLI leem CSV e XLSX em fluxo e XLS legado sob limite de
memoria, detectam estrutura, selecionam worksheet, normalizam headers,
constroem perfis e features limitadas, acumulam evidencia de
CPF/e-mail/telefone/data, calculam Dice e Levenshtein normalizado, ranqueiam
candidatos, abstêm em casos insuficientes/contraditorios, detectam colisao
exclusiva e produzem relatorio JSON protegido e explicacao. Excel inclui datas
ISO, gaps, formulas nunca avaliadas e preflight contra arquivos hostis. O
commit `1887a14` e a candidata oficial a release 0.1; nenhuma tag ou release foi
publicada.

**PLANEJADO / NAO IMPLEMENTADO:** detector completo de CNPJ e CEP, profiling
avancado, anomalias por linha, transformacao, validacao de
importacao, dry run, destinos, feedback, Hungarian, plugins dinamicos,
paralelismo, ML, embeddings e LLM.

## Evidencias locais

- `clean verify`, usando cache Maven gravavel do sandbox, passou no OpenJDK
  21.0.12 e no Temurin 25.0.4.1 LTS: 44 testes, zero falhas em cada JDK, sempre
  compilando com `release 21`.
- JaCoCo do core: 464/485 linhas (95,67%) e 280/340 branches (82,35%); gates
  85%/80% atendidos sem exclusoes.
- Fixture principal: sete headers com o destino esperado em top-1. `Documento`
  com maioria de CPFs validos prioriza `customer.document`; onze digitos nus,
  empate, ausencia de evidencia e colisao nao geram `AUTO_MAP`.
- Volume: 1.000.000 de registros, 3 colunas, 50.000.034 bytes, Java 21,
  `-Xms32m -Xmx256m`, 36,88 s, RSS maximo medido 162.364 KiB, 1.000.000 linhas
  confirmadas no relatorio. RSS e memoria total do processo, nao heap.
- Quickstart `analyze` e `explain` executado localmente sobre `examples/`.
- CSV, XLS e XLSX produziram os mesmos sete destinos top-1. Testes Excel cobrem
  ZIP64, 10.000 linhas, planilha multipla/vazia, datas, gaps, zeros de formato,
  formulas, temporarios, limites, macros, paths ZIP inseguros e rejeicao de
  relacionamento externo sem conexao de rede. A CLI possui regressao dedicada
  para `explain` de relatorio JSON 1.0.
- Documentos comunitarios e de seguranca estao presentes; fixtures e saidas
  versionadas foram auditadas e possuem proveniencia sintetica documentada.

## Limites e riscos atuais

- O commit `1887a14834269b091ada27a969adf704dc22839c` esta em `origin/main`.
  HEAD local, tracking branch e ref remota foram comparados; o tree SHA local e
  o retornado pela API do GitHub sao
  `6442528d10dba0e14f35d087cd4cfff1cb911580`.
- As execucoes do GitHub Actions, incluindo `34654764370` para o commit base do
  0.1b, nao iniciaram nenhum step: os jobs Java 21 e 25 foram recusados porque a
  conta GitHub continua bloqueada por um problema de cobranca. Portanto, a
  matriz e o quickstart continuam sem verificacao remota; isso nao representa
  falha observada no codigo ou no workflow.
- Commons CSV entrega o campo depois de aloca-lo. O limite de bytes e antecipado,
  mas `maxFieldChars` e verificado apos tokenizacao; hardening anterior a
  alocacao permanece como risco conhecido.
- XLS legado usa HSSF em memoria com teto default de 20 MiB. XLSX faz streaming
  da worksheet, mas a shared strings table do POI e materializada e limitada
  indiretamente pelo teto expandido por entrada.
- Pesos sao baseline configuravel e a calibracao continua `UNCALIBRATED`.
- A versao permanece `0.1.0-SNAPSHOT`; a mudanca para `0.1.0` depende de CI
  remoto operacional e sera feita em commit de release separado.

## Proximo passo

Resolver o bloqueio de cobranca e obter matriz 21/25 verde para o commit de
hardening. Depois, preparar o commit final `0.1.0`; publicacao de tag, release ou
artefatos exige autorizacao separada. O milestone 0.2 nao deve iniciar antes
dessa decisao.
