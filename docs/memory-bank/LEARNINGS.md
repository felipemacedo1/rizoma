# Aprendizados reutilizaveis

- A linguagem definida pelo produto e Java.
- Nao iniciar implementacao antes de o agente concluir as decisoes iniciais.
- Java 21 foi confirmado como baseline; Java 25 e a matriz de compatibilidade.
- Regras obrigatorias pertencem ao `AGENTS.md`; memoria e uma camada auxiliar.
- O memory bank nao deve conter segredos, dados importados ou suposicoes.
- Score heuristico nao deve ser apresentado como probabilidade sem calibracao.
- Estatisticas aproximadas precisam declarar metodo, amostra e precisao.
- Streaming e estruturas limitadas precedem paralelismo para grandes datasets.
- CNPJ alfanumerico esta em producao; remover letras corrompe identificadores.
- Plano de execucao futuro deve ficar vinculado a origem, schema e configuracao
  analisados.
- Na deteccao de delimitador, erro de parsing de uma alternativa nao invalida
  as demais; a alternativa malformada deve ser descartada isoladamente.
- Commons CSV nao oferece limite pre-alocacao por campo; combinar limite de
  bytes antecipado com verificacao pos-token e registrar o risco restante.
- Shade deve anexar um artefato `-all`, nao substituir o JAR principal; isso
  evita sombrear novamente um uber-JAR em verificacoes Maven repetidas.
- `java.util.zip.ZipInputStream` pode rejeitar ZIP64 valido produzido por
  SXSSF; Commons Compress le o mesmo arquivo e deve ser usado no preflight
  OOXML.
- Streaming da worksheet XLSX nao torna todo o workbook O(1): shared strings
  do POI ainda ocupam memoria e precisam de limite expandido explicito.
- Abrir `OPCPackage` por arquivo read-only evita o custo de memoria do overload
  de `InputStream`; fontes nao locais precisam de spool limitado e limpeza.
- Um relacionamento externo OPC pode ser testado sem internet: adicionar um
  alvo HTTP para um `ServerSocket` loopback e verificar timeout no `accept`
  prova que a rejeicao ocorreu sem conexao ou carregamento do alvo.
- Compatibilidade declarada de JSON precisa de fixture da versao anterior; mudar
  `formatVersion` e remover o campo introduzido em 1.1 exercita a desserializacao
  real, nao apenas uma condicao isolada de versao.
- Um CPF com checksum valido nao possui faixa reservada para testes. A fixture
  deve deixar origem sintetica explicita e nunca associar o numero a uma pessoa
  ou conta real.
