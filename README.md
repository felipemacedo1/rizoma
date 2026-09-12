# Rizoma

Rizoma e um motor Java de ingestao, profiling, mapeamento e preparacao segura de
dados. A implementacao atual recebe CSV, XLS ou XLSX e um esquema conhecido,
produz sugestoes explicaveis e executa transformacao/validacao em dry run, sem
escrever em qualquer sistema de destino. Feedback humano explicito pode ser
reutilizado como evidencia historica deterministica e auditavel. Layouts
explicitamente confirmados podem ser reconhecidos em arquivos posteriores e
seguir uma rota rapida ou adaptativa sem reutilizar o plano do arquivo antigo.

## O que funciona hoje

- CSV record-wise com UTF-8 (BOM inclusive), ISO-8859-1 ou Windows-1252;
- XLSX em streaming por worksheet e XLS legado com carga limitada por bytes;
- selecao explicita de planilha quando o workbook possui mais de uma;
- datas Excel em ISO, gaps de celulas e formulas sem avaliacao;
- preflight XLSX contra excesso de entradas, expansao, razao de compressao,
  paths inseguros, macros e relacionamentos externos;
- delimitador detectado entre virgula, ponto e virgula, tab e pipe, com override;
- aspas, aspas escapadas, delimitadores e quebras de linha dentro de campos;
- colunas identificadas pela posicao (`c0`, `c1`...), inclusive com headers iguais;
- profiling de contagens, nulidade, comprimentos, padroes, tipos e evidencias;
- cardinalidade exata ate 1.024 distintos e HyperLogLog estimado depois disso;
- top-K limitado, entropia, unique ratio e estatistica numerica incremental;
- anomalias de tipo, formato, comprimento, null, duplicidade e validade semantica;
- amostra reservoir deterministica, limitada e sempre protegida no resultado;
- normalizacao Unicode, caixa, diacriticos, camelCase, snake_case e aliases pt-BR;
- detectores de tipo fisico, CPF, e-mail, telefone brasileiro e data;
- Dice, Jaccard, Levenshtein normalizado, Jaro, Jaro-Winkler, trigramas e cosine;
- pruning explicavel para schemas grandes, preservando aliases exatos;
- score composto, cobertura, margem, contradicoes, ranking e abstencao;
- API Java sem framework e CLI `analyze`/`explain` com JSON estrito;
- corpus sintetico com avaliacao top-1/top-3, abstencao e matriz semantica.
- `MappingPlan` ligado aos fingerprints de fonte, schema e configuracao;
- transformers tipados para String, Integer, Long, BigDecimal, LocalDate,
  Boolean, CPF, telefone e CEP;
- validators required, regex, length, enum, ranges numerico/data e CPF checksum;
- dry run streaming com policies, contagens reais e erros/warnings limitados e
  mascarados;
- CLI `plan` e `dry-run`, reutilizando a mesma API Java sem porta de destino.
- feedback `CONFIRMED`, `REJECTED` e `CORRECTED`, sempre registrado por uma
  operacao explicita;
- knowledge bases `NoOp` (default), em memoria e JSON Lines limitado no
  adaptador CLI;
- historico isolado por schema/fingerprint, campo destino, nome normalizado,
  locale e contexto, com snapshot registrado no resultado e no plano;
- componente `history` separado e explicavel, com peso default limitado a 0,10.
- fingerprints separados para conteudo e layout, templates imutaveis e registry
  NoOp/InMemory sem misturar receita operacional com knowledge historico;
- rotas `FULL_ANALYSIS`, `FAST_REUSE` e `ADAPTIVE_REANALYSIS`, com drift
  estrutural/tipo/semantica e contadores do trabalho de inferencia executado;
- `MappingPlan` 1.2 com projection sources `SOURCE_COLUMN`, `CONSTANT`,
  `DERIVED` e `UNMAPPED`, alem de source columns explicitamente `IGNORED`;
- expressoes derivadas limitadas a `CONCAT`, `COALESCE`, `ADD`, `SUBTRACT`,
  `MULTIPLY` e `DIVIDE`, sem scripting, eval ou codigo dinamico.

O score e um indicador heuristico, nao uma probabilidade. A calibracao e
`UNCALIBRATED` e `AUTO_MAP` vem desligado por padrao no proprio core. Uma
sugestao nunca autoriza escrita no destino.

## Requisitos e build

- JDK 21 ou 25;
- Git; o Maven 3.9.16 e baixado pelo Wrapper.

```bash
./mvnw verify
```

O build compila com `--release 21`. O relatorio JaCoCo do core fica em
`rizoma-core/target/site/jacoco/index.html`; os gates sao 85% de linhas e 80%
de branches.

## Java 21 em cinco minutos

O artefato agregador `rizoma` traz CSV, XLS, XLSX, regras core e pt-BR. Ele
ainda nao foi publicado: enquanto estiver em desenvolvimento, execute
`./mvnw install` neste checkout e use a dependencia local:

Os imports Java usam a raiz `io.github.felipemacedo1.rizoma.*`.

```xml
<dependency>
  <groupId>io.github.felipemacedo1</groupId>
  <artifactId>rizoma</artifactId>
  <version>0.6.0-SNAPSHOT</version>
</dependency>
```

```java
TargetSchema schema = TargetSchema.builder("customer")
    .field(TargetField.builder("customer.name").name("Nome").required().build())
    .build();

Rizoma rizoma = Rizoma.create();
ProcessResult result = rizoma.process(
    ProcessRequest.of(Path.of("clientes.xlsx"), schema));

switch (result.status()) {
    case REVIEW_REQUIRED -> mostrarSugestoes(result.mappingSummary());
    case SUCCESS, SUCCESS_WITH_WARNINGS -> continuar(result.validRows());
    case INVALID -> revisarDados(result.errors());
    case FAILED -> tratarFalhaTecnica(result.errors());
}
```

Sem um plano confirmado, o resultado normal e `REVIEW_REQUIRED`; a fachada nao
converte score em autorizacao. Forneca o `MappingPlan` revisado para executar o
dry run, ou um `LayoutTemplate` para tentar `FAST_REUSE`/`ADAPTIVE_REANALYSIS`.
O teste compilado como consumidor externo fica em `rizoma-adoption-tests`.

## Quickstart da CLI

```bash
./mvnw package
java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  analyze examples/clientes.csv \
  --schema examples/customer.schema.json \
  --out target/report.json

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  explain target/report.json --column "CPF Cliente"
```

## Quickstart do dry run

O plano exige confirmacao explicita por ID posicional; ele nunca converte uma
sugestao em autorizacao automaticamente:

```bash
java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  analyze examples/dry-run-customers.csv \
  --schema examples/dry-run-customer.schema.json \
  --out target/dry-analysis.json --delimiter semicolon --header first

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  plan target/dry-analysis.json \
  --schema examples/dry-run-customer.schema.json \
  --out target/mapping.json \
  --map c0=customer.document --map c1=customer.email \
  --map c2=customer.phone --map c3=customer.postalCode \
  --map c4=customer.birthDate --map c5=customer.amount \
  --map c6=customer.active --map c7=customer.code

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  dry-run examples/dry-run-customers.csv \
  --schema examples/dry-run-customer.schema.json \
  --mapping target/mapping.json --out target/dry-run.json \
  --delimiter semicolon --header first
```

`analyze` entende estrutura/conteudo e ranqueia candidatos. `plan` registra as
escolhas confirmadas e os steps derivados. `dry-run` reabre a mesma fonte,
verifica fingerprints, transforma e valida; nao recebe sink e nao importa nada.
Policies disponiveis: `COLLECT_ERRORS` (default), `SKIP_ROW` e `FAIL_FAST`, com
`--max-errors`, `--max-issue-samples` e `--max-issue-codes`.

## Layout conhecido e projecao

Um template so nasce de uma analise e de um plano explicitamente confirmado.
Ele e reutilizavel; o plano nao e. `recognize` sempre calcula o fingerprint do
arquivo atual e, quando a compatibilidade e segura, escreve um novo plano:

```bash
java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  template create target/dry-analysis.json --mapping target/mapping.json \
  --name customer-import --out target/layout-template.json

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  recognize examples/dry-run-customers-next.csv \
  --schema examples/dry-run-customer.schema.json \
  --template target/layout-template.json --out target/recognition.json \
  --plan-out target/current-plan.json --delimiter semicolon --header first
```

Para projection plans, `plan` aceita `--ignore cN`,
`--constant target=value`, `--derive
target=MULTIPLY:source:c1,source:c2` e `--unmapped-target target`. Constantes
fazem parte da configuracao confiavel do plano; `explain-plan` mascara seu
conteudo por padrao. Nao existe JavaScript, SpEL ou dependencia entre campos
destino. O dry run avalia source columns, constantes e derivados por linha,
antes dos transformers e validators existentes.

`FAST_REUSE` exige schema/config/template compativeis, identidades inequivocas,
colunas referenciadas presentes e ausencia de contradicao de tipo/semantica na
amostra guard default de 64 linhas. Reordenacao inequivoca ainda pode ser fast.
Uma adicao ou renomeacao localizada pode seguir `ADAPTIVE_REANALYSIS`; remocao
de dependencia, duplicidade inesperada ou drift perigoso exige
`FULL_ANALYSIS`. Sampling nao garante detectar todo data drift.

## Feedback historico explicito

`analyze` nunca grava feedback. A persistencia acontece somente nos comandos
abaixo, a partir de um relatorio e schema compativeis:

```bash
java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  feedback confirm target/report.json \
  --schema examples/customer.schema.json --knowledge target/knowledge.jsonl \
  --column-id c1 --target customer.document

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  analyze examples/clientes.csv --schema examples/customer.schema.json \
  --knowledge target/knowledge.jsonl --out target/report-with-history.json

java -jar rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar \
  explain target/report-with-history.json --column-id c1
```

Tambem existem `feedback reject --target ...` e
`feedback correct --suggested-target ... --correct-target ...`. O arquivo guarda eventos JSON
Lines versionados, não células. Uma correção conta como evidência negativa para
o sugerido e positiva para o escolhido. O lookup é exato após normalização e
isolado pelo contexto do schema; não há busca fuzzy, decay temporal ou ML.

Para headers duplicados, `--column` retorna erro em vez de escolher a primeira
ocorrencia. Use, por exemplo, `--column-id c1`. `analyze` reserva stdout para
saida de maquina e envia diagnosticos seguros para stderr.

Codigos de saida: `0` sucesso, `2` argumento/contrato de entrada invalido, `3`
falha segura de analise ou I/O e `4` seletor de coluna ambiguo.

## Workflow e Extension APIs

`Rizoma` tambem delega `analyze`, `plan`, `planProjected`, `configurePlan`,
`recognizeLayout` e `dryRun` aos contratos detalhados existentes. Extensoes sao
adicionadas sem remontar os defaults:

```java
Rizoma rizoma = Rizoma.builder()
    .addValidator(new MyValidator())
    .addTransformer(new MyTransformer())
    .addSemanticDetector(new MySemanticDetector())
    .observer(event -> metrics.accept(event.stage(), event.code()))
    .build();
```

A API de alto nivel esta em `io.github.felipemacedo1.rizoma.api`; requests/results de workflow
e SPIs legitimas continuam em `io.github.felipemacedo1.rizoma.core`. Acumuladores, sketches,
pipelines e implementacoes de scoring nao sao necessarios ao consumidor comum e
nao compoem a candidata a API estavel. Consulte o
[contrato da API Java](docs/contracts/PUBLIC_JAVA_API_0.6.md).

`Rizoma`, `ProcessRequest` e `ProcessResult` sao imutaveis e reutilizaveis
sequencialmente. Uso concorrente so e seguro quando todos os readers,
detectores, transformers, validators, observers e stores fornecidos pelo
integrador tambem forem thread-safe; a versao 0.6 nao promete essa composicao.
Dry run continua sem porta de destino, e knowledge nunca e gravado
automaticamente.

## Como ler o resultado

- `score`: media ponderada somente das evidencias disponiveis;
- `coverage`: peso confiavel disponivel dividido pelo peso total configurado;
- `margin`: diferenca entre os dois melhores candidatos elegiveis; fica
  indisponivel quando existe apenas um;
- `confidenceIndex`: `score * coverage`, zerado por contradicao forte; continua
  sendo heuristico e nao percentual de probabilidade;
- `blockers`: razoes que impedem automacao, como margem baixa, colisao ou
  `AUTO_MAP` desligado;
- componentes indisponiveis: valor `null`, contribuicao zero e motivo explicito.
- `history`: suporte historico deterministico; confirmacoes elevam e rejeicoes
  suprimem o par conforme a confiabilidade limitada, sem significado
  probabilistico.

O formato do esquema e do relatorio de analise esta em
[docs/contracts/JSON_CONTRACTS_0.2.md](docs/contracts/JSON_CONTRACTS_0.2.md); os
contratos de feedback/knowledge do 0.4 estao em
[docs/contracts/JSON_CONTRACTS_0.4.md](docs/contracts/JSON_CONTRACTS_0.4.md).
Relatorios 1.3 e planos 1.2 sao produzidos atualmente; `explain` continua lendo
relatorios 1.0, 1.1 e 1.2 e a desserializacao aceita planos 1.0/1.1 sem projection
metadata. LayoutTemplate e LayoutRecognitionResult 1.0 permanecem experimentais.

## Qualidade medida

O corpus sintetico 0.2 possui cinco datasets, 28 colunas e 23 mappings
rotulados. Na execucao local atual, o modelo completo obteve 22/23 top-1,
23/23 top-3, 7/7 abstencoes corretas e 5/5 no-match. A policy simulou somente
um auto-map, correto; operacionalmente `AUTO_MAP` continua desligado.

O erro preservado e `Registro X` com documento empresarial alfanumerico, que
fica com `supplier.code` em primeiro e `supplier.document` no top-3. Nao existe
detector CNPJ no 0.2. Consulte a
[metodologia e ablation](docs/testing/CORPUS_0.2.md). Esses numeros descrevem um
corpus pequeno criado junto com o incremento; nao representam calibracao nem
generalizacao para dados externos.

O corpus separado de feedback mede quatro casos de avaliacao mantidos fora das
fixtures usadas para criar eventos. No resultado atual, history melhorou um
ranking, piorou um empate lexical deliberadamente enganoso, deixou dois
inalterados, suprimiu o candidato rejeitado e registrou um conflito em que CPF
forte venceu historico incorreto. Top-1 permaneceu 3/4; top-3 passou de 3/4 para
4/4. Esses numeros demonstram comportamento e risco, não ganho global nem
calibracao.

No volume sintetico de 1 milhao de linhas sob `-Xmx256m`, a repeticao 0.6
mediu full analyze 51,50 s, `FAST_REUSE` 1,02 s, adaptive com um binding
afetado 1,01 s e dry run 7,32 s. A repeticao 0.5 mediu
50,21/1,06/1,08/7,32 s no mesmo host, sem regressao relevante observada. Um
smoke separado de 100 mil linhas comparou Workflow e Simple API e tambem nao
mostrou overhead evidente, mas ordem e aquecimento impedem interpretar o
resultado como benchmark. Consulte a
[verificacao 0.6](docs/testing/MILESTONE_0.6.md); os tempos nao sao JMH nem
garantia de latencia.

```bash
./mvnw -pl rizoma-cli -am -Dtest=CorpusEvaluationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Limites e seguranca

Padroes de analise: 100 MiB, 10 milhoes de registros, 1.000 colunas, 1 milhao de
caracteres por campo, 10.000 por header, 20 amostras protegidas, top-3
candidatos, 100 avisos, 100 erros seguros e 10 minutos. Todos sao configuraveis
pela API; falhas estruturais sao fail-fast no 0.1a, portanto o array de erros
de um resultado bem-sucedido normalmente fica vazio.
O limite de bytes e aplicado antes e durante CSV. Apache Commons CSV nao oferece
limite pre-alocacao por campo, portanto um campo hostil ainda pode causar uma
alocacao ate o limite total do arquivo.

Por coluna, o profiler mantem no maximo 1.024 frequencias exatas, 10 contadores
Space-Saving, 1.024 registradores HLL, 20 anomalias e 100 explicacoes de poda.
Quando mais candidatos sao podados, o total e informado por aviso seguro. HLL declara
`ESTIMATED` e erro relativo esperado de aproximadamente 3,25%. Top-values e
localizacoes de anomalia nunca publicam a celula bruta.

Para XLSX, os defaults adicionais sao: 10.000 entradas ZIP, 512 MiB expandidos,
128 MiB por entrada, razao compactado/expandido minima de 0,01 e 100 planilhas.
XLS legado usa o modelo em memoria do POI com teto proprio de 20 MiB por padrao.
Esses limites podem ser alterados por `AnalysisOptions.readerOptions`. Fontes
XLSX locais sao abertas read-only por acesso aleatorio; fontes nao locais usam
temporario limitado, fechado e removido. O parser XML desabilita DTD e entidades
externas. Formulas aceitam `cached` (default), `expression` ou `reject`; nenhuma
politica executa a formula. Em workbooks com mais de uma planilha, use
`--sheet "Nome"` ou `--sheet 0`.

Relacionamentos externos de qualquer tipo sao rejeitados a partir dos
metadados do pacote antes da leitura da worksheet. O reader nao resolve nem
acessa o destino externo; esse comportamento possui teste com listener local
que confirma zero conexoes.

Valores brutos so existem transitoriamente para detectores autorizados. O
relatorio contem agregados e amostras como `<redacted:length=N>`, mesmo quando o
tipo semantico nao foi reconhecido. O motor nao usa rede, nao altera a fonte e
compara o SHA-256 antes e depois da leitura para detectar mudanca durante a
analise.

No dry run, o default coleta no maximo 1.000 erros antes de encerrar, retem 100
exemplos protegidos e no maximo 256 codigos/campos distintos. Totais processados
permanecem exatos ate eventual parada controlada. O relatorio nao contem valor
original nem transformado. Datas com barra sem locale/formato confiavel e
decimais com separadores sem locale falham como ambiguos; identificadores TEXT
com zeros iniciais nao sao convertidos.

O arquivo de knowledge usa no maximo 10 MiB, 100.000 eventos e 16 KiB por linha
nos defaults da CLI. Leitura valida todo o arquivo sob lock, rejeita linha final
truncada, JSON desconhecido/corrompido, duplicata conflitante, symlink e arquivo
nao regular. Novos arquivos recebem permissao `0600` em sistemas POSIX. O path
e fornecido explicitamente pelo operador; eventos nao controlam paths. O
adaptador e indicado para uso local com um writer cooperativo, nao para
concorrencia distribuida ou filesystem de rede.

## Limitacoes atuais

CNPJ completo, detector semantico de CEP, quantis/outliers robustos, unique,
foreign key, escrita em destino, matching global, plugins
dinamicos, ML e LLM nao estao implementados. Dry run valida somente regras
locais configuradas; nao declara dados prontos para producao. Linhas CSV
irregulares continuam sendo erro estrutural fail-fast seguro, nao resultado
parcial. O arquivo de 1 milhao de linhas nao e versionado; execute
`scripts/volume-smoke.sh` para gera-lo em streaming e valida-lo com `-Xmx256m`.

No XLSX, a worksheet e lida em streaming, mas a tabela de strings compartilhadas
do POI ainda ocupa memoria e fica protegida indiretamente pelo limite expandido
por entrada. Celulas mescladas nao sao propagadas: somente a ancora possui valor.
O XLS legado nao e streaming. Valores `cached` de formula podem estar obsoletos,
pois o Rizoma deliberadamente nao recalcula workbooks. Os artefatos estao em
`0.6.0-SNAPSHOT` e ainda nao foram publicados em registry ou release. JMH foi
adiado ate o subscore lexical estabilizar; o custo atual e acompanhado pelo
teste end-to-end de volume.

Planos e regexes customizadas sao configuracao confiavel. `planId` nao e
assinatura digital, e uma regex Java patologica nao possui timeout isolado
dentro de uma linha. O historico faz lookup somente por nome normalizado exato;
nao ha recencia/decay, namespace de organizacao dedicado, compactacao,
recuperacao automatica de arquivo parcial ou coordenacao multi-host.

O registry persistente de layouts nao foi implementado: o core oferece NoOp e
InMemory, e a CLI demonstra reutilizacao lendo um template JSON explicito. A
rota adaptativa cobre adicao irrelevante e uma renomeacao localizada; mudancas
multiplas ou ambiguas escalam conservadoramente para analise completa. O guard
le apenas a amostra configurada, embora o SHA-256 do conteudo percorra o arquivo
inteiro. Matching global/Hungarian foi adiado por falta de evidencia de que um
modelo one-to-one represente projection mappings reais.

## Fixtures e dados

Todas as fixtures versionadas sao sinteticas e nao representam pessoas ou
sistemas reais. Consulte [examples/README.md](examples/README.md) para a
proveniencia e as regras de contribuicao. Relatorios gerados ficam fora do Git;
amostras publicas sao mascaradas mesmo quando o tipo semantico e desconhecido.

## Comunidade e seguranca

- [Como contribuir](CONTRIBUTING.md)
- [Codigo de conduta](CODE_OF_CONDUCT.md)
- [Politica de seguranca](SECURITY.md)
- [Changelog](CHANGELOG.md)

## Documentacao

- [Especificacao tecnica](docs/architecture/TECHNICAL_SPECIFICATION.md)
- [Contrato JSON 0.2](docs/contracts/JSON_CONTRACTS_0.2.md)
- [Contratos de plano e dry run 0.3](docs/contracts/JSON_CONTRACTS_0.3.md)
- [Contratos de feedback e knowledge 0.4](docs/contracts/JSON_CONTRACTS_0.4.md)
- [Templates, reconhecimento e JSON 0.5](docs/contracts/JSON_CONTRACTS_0.5.md)
- [Data projection 0.5](docs/contracts/DATA_PROJECTION_0.5.md)
- [API Java publica 0.6](docs/contracts/PUBLIC_JAVA_API_0.6.md)
- [Avaliacao de feedback 0.4](docs/testing/MILESTONE_0.4.md)
- [Verificacao do milestone 0.5](docs/testing/MILESTONE_0.5.md)
- [Verificacao do milestone 0.6](docs/testing/MILESTONE_0.6.md)
- [Corpus e metricas 0.2](docs/testing/CORPUS_0.2.md)
- [Roadmap](docs/roadmap.md)
- [Estado atual](docs/memory-bank/CURRENT.md)
- [Decisoes](docs/memory-bank/DECISIONS.md)
- [Licenca Apache-2.0](LICENSE)
