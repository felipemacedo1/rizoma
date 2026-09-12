# Rizoma

Rizoma e um motor Java de ingestao, profiling, mapeamento e preparacao segura de
dados. A implementacao atual recebe CSV, XLS ou XLSX e um esquema conhecido,
produz sugestoes explicaveis e executa transformacao/validacao em dry run, sem
escrever em qualquer sistema de destino.

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

## Quickstart da CLI

```bash
./mvnw package
java -jar rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar \
  analyze examples/clientes.csv \
  --schema examples/customer.schema.json \
  --out target/report.json

java -jar rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar \
  explain target/report.json --column "CPF Cliente"
```

## Quickstart do dry run

O plano exige confirmacao explicita por ID posicional; ele nunca converte uma
sugestao em autorizacao automaticamente:

```bash
java -jar rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar \
  analyze examples/dry-run-customers.csv \
  --schema examples/dry-run-customer.schema.json \
  --out target/dry-analysis.json --delimiter semicolon --header first

java -jar rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar \
  plan target/dry-analysis.json \
  --schema examples/dry-run-customer.schema.json \
  --out target/mapping.json \
  --map c0=customer.document --map c1=customer.email \
  --map c2=customer.phone --map c3=customer.postalCode \
  --map c4=customer.birthDate --map c5=customer.amount \
  --map c6=customer.active --map c7=customer.code

java -jar rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar \
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

Para headers duplicados, `--column` retorna erro em vez de escolher a primeira
ocorrencia. Use, por exemplo, `--column-id c1`. `analyze` reserva stdout para
saida de maquina e envia diagnosticos seguros para stderr.

Codigos de saida: `0` sucesso, `2` argumento/contrato de entrada invalido, `3`
falha segura de analise ou I/O e `4` seletor de coluna ambiguo.

## API Java

Os artefatos ainda nao foram publicados em um registry. Dentro deste repositorio,
os modulos podem ser usados diretamente no reactor Maven:

```java
List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
detectors.addAll(PtBrDetectors.defaults());
List<ValueTransformer<?, ?>> transformers = new ArrayList<>(BuiltInTransformers.defaults());
transformers.addAll(PtBrExecutionRules.transformers());
List<Validator<?>> validators = new ArrayList<>(BuiltInValidators.defaults());
validators.addAll(PtBrExecutionRules.validators());

MappingEngine engine = MappingEngine.builder()
    .readers(List.of(new ExcelDataReader(), new CsvDataReader()))
    .semanticDetectors(detectors)
    .transformers(transformers)
    .validators(validators)
    .normalizer(PtBrHeaderRules.normalizer())
    .configuration(EngineConfig.defaults())
    .build();

TargetSchema schema = new TargetSchema("customer", "1", "customer", "pt-BR", List.of(
    new TargetField("customer.document", "CPF", List.of("Documento"),
        PhysicalType.TEXT, Set.of(new SemanticType("br:cpf")), true)
));

PathTabularSource source = new PathTabularSource(Path.of("examples/clientes.csv"));
AnalysisOptions options = new AnalysisOptions(Map.of("header", "detect"), 42L);
AnalysisResult result = engine.analyze(new AnalysisRequest(source, schema, options));

var best = result.candidatesByColumn().get("c1").getFirst();
best.components().forEach(component ->
    System.out.println(component.id() + ": " + component.evidence()));

MappingPlan plan = new MappingPlanner().create(result, schema, List.of(
    new MappingPlanner.Selection("c1", "customer.document", "confirmed by caller")
));
DryRunResult dryRun = engine.dryRun(new DryRunRequest(
    source, schema, plan, options, DryRunOptions.defaults()));
System.out.println(dryRun.rowsValid() + " valid rows");
```

Fonte, esquema e opcoes pertencem a `AnalysisRequest`; o engine nao guarda
estado de uma execucao. A composicao e imutavel e suporta reutilizacao
sequencial. Uso concorrente nao e prometido porque componentes injetados podem
possuir restricoes proprias. `dryRun` nao aceita destino; escrita real nao foi
implementada.

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

O formato do esquema e do relatorio de analise esta em
[docs/contracts/JSON_CONTRACTS_0.2.md](docs/contracts/JSON_CONTRACTS_0.2.md); os
contratos experimentais de plano/dry run estao em
[docs/contracts/JSON_CONTRACTS_0.3.md](docs/contracts/JSON_CONTRACTS_0.3.md).
Relatorios 1.2 sao produzidos atualmente; o comando `explain` continua lendo
relatorios 1.0 e 1.1 para compatibilidade.

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

## Limitacoes atuais

CNPJ completo, detector semantico de CEP, quantis/outliers robustos, unique,
foreign key, escrita em destino, feedback persistente, matching global, plugins
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
`0.3.0-SNAPSHOT` e ainda nao foram publicados em registry ou release. JMH foi
adiado ate o subscore lexical estabilizar; o custo atual e acompanhado pelo
teste end-to-end de volume.

Planos e regexes customizadas sao configuracao confiavel no 0.3. `planId` nao e
assinatura digital, e uma regex Java patologica nao possui timeout isolado
dentro de uma linha.

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
- [Corpus e metricas 0.2](docs/testing/CORPUS_0.2.md)
- [Roadmap](docs/roadmap.md)
- [Estado atual](docs/memory-bank/CURRENT.md)
- [Decisoes](docs/memory-bank/DECISIONS.md)
- [Licenca Apache-2.0](LICENSE)
