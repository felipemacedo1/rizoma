# Estado atual

Atualizado em: 2026-09-11

## Confirmado

- Nome do projeto: Rizoma.
- Linguagem: Java.
- Objetivo: motor deterministico de ingestao, profiling, mapeamento, validacao,
  transformacao e migracao de dados, inicialmente CSV, XLS e XLSX.
- Repositorio publico `felipemacedo1/rizoma` criado e branch `main` publicada.
- Workspace Codex autonomo configurado, com memoria nativa habilitada.
- Baseline Java 21, testado tambem em Java 25; sem features preview no core.
- Build tool Maven 3.9.x via Maven Wrapper.
- Arquitetura: monolito modular de biblioteca com core independente de framework.
- Primeiro adaptador executavel: CLI; nenhum servidor web no MVP.
- Modulos planejados: core, CSV, Excel, locale pt-BR e CLI.
- Especificacao consolidada em `docs/architecture/TECHNICAL_SPECIFICATION.md`.
- Primeiro incremento: 0.1a, analise CSV explicavel.

## Decisoes pendentes da implementacao

- Versoes exatas das dependencias, verificadas quando o build for criado.
- Formato JSON final do target schema e do relatorio.
- Valores seguros padrao para limites de arquivos.
- Pesos e thresholds calibrados com corpus; os atuais sao baseline configuravel.
- Implementacoes de sketches, decididas por benchmark/erro observado.
- Substituicao da LICENSE MIT por Apache-2.0 antes do primeiro codigo.

## Estado de implementacao

**IMPLEMENTADO:** especificacao, revisao critica, arquitetura e roadmap.

**NAO IMPLEMENTADO:** todo codigo de producao, testes, CLI, readers e benchmarks.

**PLANEJADO:** capacidades posteriores descritas no roadmap. Nao ha alegacao de
funcionalidade executavel nesta etapa.

## Proximo passo

Implementar 0.1a incrementalmente: build minimo, core, CSV streaming,
normalizacao/profiling, metricas, scoring e CLI, compilando e testando cada
etapa antes de avancar.
