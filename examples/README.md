# Fixtures de exemplo

Todos os arquivos deste diretorio sao sinteticos e existem somente para testes,
documentacao e quickstart. Eles nao representam pessoas, empresas, enderecos ou
contas reais e nao foram extraidos de sistemas de producao.

Alguns valores de CPF possuem forma e checksum validos porque o detector precisa
dessa propriedade para ser testado. Nao existe faixa oficial reservada de CPF
para documentacao; esses numeros nao devem ser usados como identificadores reais.
Os nomes sao genericos, os enderecos sao ficticios e os e-mails usam o dominio
reservado `.test`.

Contribuicoes devem manter essa regra: nao adicione dados pessoais, dumps,
credenciais, tokens ou arquivos corporativos. Novas fixtures precisam ser
pequenas, sinteticas, reproduziveis e ter a finalidade do caso de teste descrita.

O corpus rotulado do milestone 0.2 segue a mesma politica e esta documentado em
[`corpus/README.md`](../corpus/README.md).

`dry-run-customers.csv` e `dry-run-customer.schema.json` exercitam o pipeline
0.3 com representacoes validas e invalidas de CPF, e-mail, telefone, CEP, data,
decimal e booleano, alem de identificadores com zeros iniciais. Todos os nomes,
dominios e valores foram criados especificamente para o repositorio. Os CPFs
com checksum valido nao estao associados a qualquer pessoa.

`dry-run-customers-next.csv` mantem a mesma estrutura com conteudo sintetico
novo para demonstrar reconhecimento `FAST_REUSE` e a criacao de um novo plano
vinculado ao fingerprint do segundo arquivo.

Exemplos Java compilados da camada de adocao ficam em
`rizoma-adoption-tests/src/main/java/io/github/rizoma/examples`: processamento
simples, workflow controlado, validator customizado e layout conhecido. O
modulo depende diretamente apenas do artefato agregador `rizoma`.
