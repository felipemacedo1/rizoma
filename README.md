# Rizoma

Rizoma e um motor Java de ingestao, profiling e sugestao de mapeamento de dados.
A implementacao atual recebe CSV, XLS ou XLSX e um esquema conhecido e produz
um relatorio explicavel, sem escrever em qualquer sistema de destino.

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
- profiling exato de contagens, nulidade, comprimento, votos de tipo e evidencias;
- amostra reservoir deterministica, limitada e sempre protegida no resultado;
- normalizacao Unicode, caixa, diacriticos, camelCase, snake_case e aliases pt-BR;
- detectores de tipo fisico, CPF, e-mail, telefone brasileiro e data;
- Dice por tokens e Levenshtein normalizado;
- score composto, cobertura, margem, contradicoes, ranking e abstencao;
- API Java sem framework e CLI `analyze`/`explain` com JSON estrito.

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
java -jar rizoma-cli/target/rizoma-cli-0.1.0-SNAPSHOT-all.jar \
  analyze examples/clientes.csv \
  --schema examples/customer.schema.json \
  --out target/report.json

java -jar rizoma-cli/target/rizoma-cli-0.1.0-SNAPSHOT-all.jar \
  explain target/report.json --column "CPF Cliente"
```

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

MappingEngine engine = MappingEngine.builder()
    .readers(List.of(new ExcelDataReader(), new CsvDataReader()))
    .semanticDetectors(detectors)
    .normalizer(PtBrHeaderRules.normalizer())
    .configuration(EngineConfig.defaults())
    .build();

TargetSchema schema = new TargetSchema("customer", "1", "customer", "pt-BR", List.of(
    new TargetField("customer.document", "CPF", List.of("Documento"),
        PhysicalType.TEXT, Set.of(new SemanticType("br:cpf")), true)
));

AnalysisResult result = engine.analyze(new AnalysisRequest(
    new PathTabularSource(Path.of("clientes.xlsx")), schema,
    new AnalysisOptions(Map.of("sheet", "Clientes", "formula", "cached"), 42L)));

var best = result.candidatesByColumn().get("c0").getFirst();
best.components().forEach(component ->
    System.out.println(component.id() + ": " + component.evidence()));
```

Fonte, esquema e opcoes pertencem a `AnalysisRequest`; o engine nao guarda
estado de uma execucao. A composicao e imutavel e suporta reutilizacao
sequencial. Uso concorrente nao e prometido porque readers e detectores
injetados podem possuir restricoes proprias.

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

O formato do esquema e do relatorio esta em
[docs/contracts/JSON_CONTRACTS_0.1B.md](docs/contracts/JSON_CONTRACTS_0.1B.md).

## Limites e seguranca

Padroes: 100 MiB, 10 milhoes de registros, 1.000 colunas, 1 milhao de
caracteres por campo, 10.000 por header, 20 amostras protegidas, top-3
candidatos, 100 avisos, 100 erros seguros e 10 minutos. Todos sao configuraveis
pela API; falhas estruturais sao fail-fast no 0.1a, portanto o array de erros
de um resultado bem-sucedido normalmente fica vazio.
O limite de bytes e aplicado antes e durante CSV. Apache Commons CSV nao oferece
limite pre-alocacao por campo, portanto um campo hostil ainda pode causar uma
alocacao ate o limite total do arquivo.

Para XLSX, os defaults adicionais sao: 10.000 entradas ZIP, 512 MiB expandidos,
128 MiB por entrada, razao compactado/expandido minima de 0,01 e 100 planilhas.
XLS legado usa o modelo em memoria do POI com teto proprio de 20 MiB por padrao.
Esses limites podem ser alterados por `AnalysisOptions.readerOptions`. Fontes
XLSX locais sao abertas read-only por acesso aleatorio; fontes nao locais usam
temporario limitado, fechado e removido. O parser XML desabilita DTD e entidades
externas. Formulas aceitam `cached` (default), `expression` ou `reject`; nenhuma
politica executa a formula. Em workbooks com mais de uma planilha, use
`--sheet "Nome"` ou `--sheet 0`.

Valores brutos so existem transitoriamente para detectores autorizados. O
relatorio contem agregados e amostras como `<redacted:length=N>`, mesmo quando o
tipo semantico nao foi reconhecido. O motor nao usa rede, nao altera a fonte e
compara o SHA-256 antes e depois da leitura para detectar mudanca durante a
analise.

## Limitacoes atuais

CNPJ completo, CEP semantico, Jaro-Winkler, distribuicoes/cardinalidade,
anomalias por linha, transformacao, validacao de importacao, dry run, destino,
feedback persistente, matching global, plugins dinamicos, ML e LLM nao estao
implementados. O arquivo de 1 milhao de linhas nao e versionado; execute
`scripts/volume-smoke.sh` para gera-lo em streaming e valida-lo com `-Xmx256m`.

No XLSX, a worksheet e lida em streaming, mas a tabela de strings compartilhadas
do POI ainda ocupa memoria e fica protegida indiretamente pelo limite expandido
por entrada. Celulas mescladas nao sao propagadas: somente a ancora possui valor.
O XLS legado nao e streaming. Valores `cached` de formula podem estar obsoletos,
pois o Rizoma deliberadamente nao recalcula workbooks. Os artefatos permanecem
`SNAPSHOT` e ainda nao foram publicados em registry ou release.

## Documentacao

- [Especificacao tecnica](docs/architecture/TECHNICAL_SPECIFICATION.md)
- [Roadmap](docs/roadmap.md)
- [Estado atual](docs/memory-bank/CURRENT.md)
- [Decisoes](docs/memory-bank/DECISIONS.md)
- [Licenca Apache-2.0](LICENSE)
