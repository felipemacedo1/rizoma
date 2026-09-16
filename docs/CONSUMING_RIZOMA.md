# Consumindo o Rizoma

O Rizoma é uma biblioteca Java 21+ para analisar CSV, XLS e XLSX, sugerir
mapeamentos explicáveis e executar um **dry run** de um plano confirmado. Ele
não importa dados em um destino e nunca confirma sugestões automaticamente.

A API pública de adoção ainda é experimental antes da versão 1.0; consulte o
[contrato da API 0.6](contracts/PUBLIC_JAVA_API_0.6.md) antes de depender de
tipos avançados.

## Dependência

Após a publicação da primeira release no Maven Central, use o artefato
agregador. Ele já traz core, CSV, Excel e regras pt-BR:

```xml
<dependency>
  <groupId>io.github.felipemacedo1</groupId>
  <artifactId>rizoma</artifactId>
  <version>0.6.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.felipemacedo1:rizoma:0.6.0")
```

> A versão publicada ainda não existe. Até a release, clone este repositório e
> execute `./mvnw clean install`; então substitua a versão acima por
> `0.6.0-SNAPSHOT` no projeto consumidor local.

## Pré-requisitos

- Java 21 ou superior;
- Maven 3.9+ (ou Gradle compatível);
- uma fonte CSV, XLS ou XLSX reabrível;
- um `TargetSchema` que represente o contrato do seu sistema de destino.

Sistemas Java 8 não devem tentar carregar a biblioteca in-process. Use a CLI
como processo externo e troque arquivos/JSON versionado.

## Integração mínima: analisar e pedir revisão

```java
import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessResult;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.core.PhysicalType;
import io.github.felipemacedo1.rizoma.core.TargetField;
import io.github.felipemacedo1.rizoma.core.TargetSchema;

import java.nio.file.Path;

TargetSchema schema = TargetSchema.builder("customer")
    .version("1")
    .locale("pt-BR")
    .field(TargetField.builder("customer.name")
        .name("Nome")
        .aliases("Nome Completo")
        .required()
        .build())
    .field(TargetField.builder("customer.email")
        .name("E-mail")
        .type(PhysicalType.TEXT)
        .semanticType("core:email")
        .build())
    .build();

Rizoma rizoma = Rizoma.create();

ProcessResult result = rizoma.process(
    ProcessRequest.of(Path.of("clientes.xlsx"), schema));

if (result.reviewRequired()) {
    // Exiba result.mappingSummary().suggestions() para uma pessoa confirmar.
}
```

Sem um plano confirmado, o resultado esperado é `REVIEW_REQUIRED`. Isso é uma
proteção: score e `confidenceIndex` são heurísticas não calibradas, não uma
autorização de importação automática.

## Fluxo recomendado: revisão → plano → dry run

Após seu usuário ou uma regra de negócio confirmar o mapeamento, crie um plano
ligado à análise e rode o dry run:

```java
import java.util.List;
import java.util.Map;

var analysis = result.analysis().orElseThrow();

var plan = rizoma.plan(
    analysis,
    schema,
    Map.of(
        "c0", "customer.name",
        "c1", "customer.email"),
    List.of());

ProcessResult checked = rizoma.process(
    ProcessRequest.builder(Path.of("clientes.xlsx"), schema)
        .mappingPlan(plan)
        .build());

if (checked.canContinue()) {
    long validRows = checked.validRows();
    // Seu sistema, fora do Rizoma, decide como persistir as linhas válidas.
} else if (checked.status() == io.github.felipemacedo1.rizoma.api.ProcessStatus.INVALID) {
    // Consulte checked.errors() e checked.dryRun() para tratar dados inválidos.
}
```

O plano é vinculado ao fingerprint do arquivo analisado. Reutilizá-lo para
conteúdo diferente é rejeitado; analise o novo arquivo em vez de tentar
contornar essa guarda.

## Fontes de dados

Prefira `Path` para arquivos grandes:

```java
var source = io.github.felipemacedo1.rizoma.api.Sources.from(
    Path.of("clientes.csv"));
```

Também existem `Sources.from(byte[], fileName)` e
`Sources.from(InputStream, fileName, maxBytes)`. A origem por stream é
materializada em memória para permitir múltiplas leituras e fingerprint; forneça
um limite explícito e feche o stream no código chamador.

## Layouts recorrentes

Quando a mesma planilha chega repetidamente, crie e mantenha um
`LayoutTemplate` ou um `LayoutRegistry` sob controle do seu aplicativo.
O Rizoma poderá usar `FAST_REUSE` ou `ADAPTIVE_REANALYSIS` apenas depois das
guardas de compatibilidade; em caso de drift, ele retorna à análise/revisão.
Não trate uma rota rápida como uma confirmação implícita.

## Operação e limites

- O Rizoma é **stateless** em relação ao destino: não grava banco, não chama API
  de terceiros e não possui sink de importação.
- `ProcessRequest` e `ProcessResult` são imutáveis. Reutilize uma instância
  de `Rizoma` sequencialmente; concorrência só é segura se todas as extensões
  injetadas também forem thread-safe.
- A API 0.x pode sofrer mudanças incompatíveis. Fixe versões e leia o
  [CHANGELOG](../../CHANGELOG.md) ao atualizar.
- Para extensões (readers, detectores, transformadores ou validadores), use
  `Rizoma.builder()`; mantenha detalhes de implementação fora da integração
  comum.

## Onde aprofundar

- [Contrato da API pública 0.6](contracts/PUBLIC_JAVA_API_0.6.md)
- [Exemplos compilados como consumidor externo](../rizoma-adoption-tests/src/main/java/io/github/felipemacedo1/rizoma/examples)
- [Changelog](../CHANGELOG.md)
- [Segurança](../SECURITY.md)
