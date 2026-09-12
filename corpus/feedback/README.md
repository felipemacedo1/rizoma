# Corpus sintetico de feedback 0.4

`development/` fornece somente eventos historicos; `evaluation/` mede o efeito
em fontes separadas. Nenhuma linha representa pessoa, empresa ou sistema real.
Os identificadores, documentos e dominios foram criados para teste.

Os rotulos do corpus 0.2 nao sao alterados. Este corpus mede influencia,
supressao, conflito e invariancia; nao calibra score nem representa aprendizado
de maquina.

Casos de desenvolvimento e avaliacao nunca compartilham linhas. Eles podem
compartilhar a identidade normalizada do header porque essa e precisamente a
chave historica sob teste. `misleading.schema.json` cria um empate lexical
deliberado para preservar um caso em que feedback ruim piora o ranking;
`document.csv` verifica que conteudo CPF forte ainda vence feedback incorreto.

Rotulos esperados da avaliacao:

- `code.csv` -> `customer.code`;
- `supplier.csv` -> `supplier.document`;
- `document.csv` -> `customer.document`;
- `misleading.csv` -> `customer.code` (risco controlado);
- `contact.csv` mede somente supressao de `customer.email` rejeitado.
