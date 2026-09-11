# Contribuindo com o Rizoma

Obrigado por considerar uma contribuicao. O Rizoma e uma biblioteca Java para
ingestao, profiling e sugestao explicavel de mapeamentos. O projeto prioriza
correcao, seguranca de dados e incrementos verticais pequenos.

## Antes de comecar

- Leia o [README](README.md), a
  [especificacao tecnica](docs/architecture/TECHNICAL_SPECIFICATION.md) e o
  [estado atual](docs/memory-bank/CURRENT.md).
- Para mudancas significativas, abra uma issue descrevendo o problema, os casos
  de uso e os limites esperados antes de investir em uma implementacao extensa.
- Nao envie dados pessoais, arquivos corporativos, credenciais ou amostras sem
  autorizacao. Fixtures devem ser sinteticas e documentadas.
- Vulnerabilidades seguem o processo de [SECURITY.md](SECURITY.md), nao uma
  issue publica com detalhes exploraveis.

## Ambiente

- JDK 21 e o baseline de compilacao, sem recursos preview.
- Java 25 integra a matriz de compatibilidade.
- Use o Maven Wrapper versionado; nao e necessario instalar Maven globalmente.

```bash
./mvnw verify
```

O core deve continuar dependente apenas do JDK. Dependencias de formato ficam
nos adaptadores correspondentes; regras brasileiras ficam em
`rizoma-locale-ptbr`.

## Mudancas e testes

1. Crie uma branch a partir de `main` atualizada.
2. Implemente a menor alteracao coesa que resolva o problema.
3. Adicione testes deterministas proporcionais ao risco.
4. Execute `./mvnw clean verify` no Java 21 e, quando disponivel, no Java 25.
5. Execute o quickstart do README se alterar API, CLI, leitores ou JSON.
6. Atualize somente a documentacao afetada e o memory bank quando houver
   conhecimento duravel novo.

Para leitores e profiling, preserve streaming e limites de memoria. Para
matching, toda mudanca de score precisa manter explicacoes consistentes e nao
deve transformar heuristica em alegacao de probabilidade. `AUTO_MAP` permanece
desligado por padrao salvo decisao arquitetural explicita e testada.

## Pull requests

Inclua no PR:

- problema resolvido e escopo deliberadamente excluido;
- comandos executados e resultados reais;
- impacto em contratos publicos ou formato JSON;
- riscos de seguranca, privacidade e memoria;
- documentacao alterada.

Mantenha commits focados e mensagens claras. Nao inclua `target/`, relatorios,
arquivos de volume ou configuracoes pessoais de IDE. Nao use force-push em
historico compartilhado.

Ao submeter uma contribuicao, voce declara que tem direito de envia-la. Salvo
indicacao explicita em contrario, contribuicoes aceitas ficam sob os termos da
[Apache License 2.0](LICENSE), conforme a secao 5 da licenca.
