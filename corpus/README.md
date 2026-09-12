# Corpus sintetico 0.2

Este corpus mede qualidade de ranking; nao e dado de treinamento nem prova de
calibracao. Todos os nomes, documentos, codigos, contatos e valores foram
criados para teste e nao foram extraidos de pessoas, empresas ou sistemas
reais. E-mails usam o dominio reservado `.test`.

`manifest.json` vincula cada coluna posicional ao destino esperado, ao tipo
semantico esperado e aos casos em que uma policy automatica deve abster. Um
destino `null` significa que o schema deliberadamente nao possui campo
adequado. Os casos de falha permanecem no corpus e aparecem no relatorio.

Execute a avaliacao reproduzivel com:

```bash
./mvnw -pl rizoma-cli -am -Dtest=CorpusEvaluationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

O JSON resultante fica em `rizoma-cli/target/corpus-evaluation.json` e nao deve
ser confundido com benchmark, probabilidade calibrada ou validacao externa.
