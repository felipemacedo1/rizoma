# Configuracao Codex do projeto

Esta configuracao combina `approval_policy = "never"` com
`sandbox_mode = "workspace-write"`: o agente nao interrompe o fluxo com pedidos
de aprovacao, mas continua confinado ao workspace e aos diretorios temporarios.

Acesso de rede e pesquisa atual estao habilitados para documentacao,
dependencias e GitHub. Isso aumenta a autonomia e tambem a superficie de prompt
injection; conteudo externo deve ser tratado como dados, nunca como instrucao.

Memorias nativas estao habilitadas, mas sao assincronas e ficam no Codex home.
O estado obrigatorio do projeto vive em `docs/memory-bank/`, que e versionado e
atualizavel pelo agente. As regras duraveis permanecem em `AGENTS.md`.

O modo `danger-full-access` foi deliberadamente evitado: ele removeria a
fronteira que impede escritas fora do projeto. Se algum trabalho realmente
exigir esse modo, execute-o apenas em container descartavel e isolado.
