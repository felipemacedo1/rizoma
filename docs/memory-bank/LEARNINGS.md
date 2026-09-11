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
