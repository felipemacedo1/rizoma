# Avaliacao sintetica do milestone 0.2

## Metodo

O corpus versionado em `corpus/` contem cinco dominios independentes:
clientes, fornecedores, pedidos, estoque e financeiro. Sao 28 colunas, das
quais 23 possuem destino rotulado, cinco sao `NO_MATCH` e sete exigem
abstencao da policy automatica. Todos os dados sao sinteticos.

Metricas:

- **top-1 accuracy:** acertos em primeiro / 23 colunas mapeaveis;
- **top-3 recall:** destino esperado entre os tres primeiros / 23;
- **auto-map precision simulada:** acertos entre candidatos que passariam score,
  coverage, margem, elegibilidade e conflito; `AUTO_MAP` real continua off;
- **abstention quality:** casos marcados ambiguos/no-match que a simulacao nao
  automatizaria / sete;
- **false-positive rate:** auto-maps simulados incorretos / 28 colunas;
- **no-match accuracy:** decisoes `NO_MATCH` / cinco casos sem destino.

Nenhuma dessas taxas calibra score ou confidence. O corpus e pequeno e foi
criado junto com o incremento; nao existe threshold de sucesso estatistico.

## Resultado local observado

| Configuracao | Top-1 | Top-3 | Auto-map simulado | Precision | Abstencao | No-match |
|---|---:|---:|---:|---:|---:|---:|
| baseline lexical 0.1 + score completo | 22/23 | 23/23 | 2 | 2/2 | 6/7 | 5/5 |
| lexical 0.2 somente | 22/23 | 22/23 | 19 | 19/19 | 5/7 | 5/5 |
| lexical + semantica | 22/23 | 23/23 | 2 | 2/2 | 6/7 | 5/5 |
| lexical + semantica + padrao | 22/23 | 23/23 | 2 | 2/2 | 6/7 | 5/5 |
| modelo completo 0.2 | 22/23 | 23/23 | 1 | 1/1 | 7/7 | 5/5 |

O subscore 0.2 nao aumentou top-1 nesse corpus. O ganho observado foi
conservador: um auto-map ambiguo do baseline deixou de ser elegivel, elevando a
abstencao de 85,71% para 100%. Lexical isolado parece forte nos aliases, mas
reduz top-3 e abstencao; portanto, nao justifica remover sinais de conteudo.

## Falha preservada

`fornecedores.c1` usa o header ruim `Registro X` e conteudo empresarial
alfanumerico. O esperado e `supplier.document`; o top-1 atual e
`supplier.code`, embora o correto apareca no top-3. A causa e conhecida: o 0.2
nao implementa detector CNPJ e nao ha pista lexical suficiente. O caso nao foi
adaptado para fazer o score passar.

## Matriz semantica resumida

| Esperado | Predito igual | UNKNOWN | Outro |
|---|---:|---:|---:|
| `br:cpf` | 1 | 0 | 0 |
| `br:phone` | 2 | 0 | 0 |
| `core:email` | 1 | 0 | 0 |
| `core:date` | 1 | 2 | 0 |
| `br:cnpj` | 0 | 1 | 0 |
| `UNKNOWN` | 20 | 0 | 0 |

As duas datas `UNKNOWN` sao intencionais: misturam formatos ambiguos e valor
invalido, deixando confidence abaixo de 0,5. CNPJ permanece uma lacuna.

## Reproducao

```bash
./mvnw -pl rizoma-cli -am -Dtest=CorpusEvaluationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

O relatorio completo e gerado em
`rizoma-cli/target/corpus-evaluation.json`. O teste executa o modelo completo
duas vezes e exige igualdade estrutural para comprovar determinismo.

## Custo observado

O smoke test de 1.000.000 de linhas/3 colunas, Java 21 e `-Xmx256m` passou:

| Versao | Duracao | RSS maximo | Heap observado antes de GC |
|---|---:|---:|---:|
| baseline 0.1 | 36,88 s | 162.364 KiB | nao medido |
| 0.2 (execucao final) | 36,46 s | 172.172 KiB | 51.735 KiB |

Uma repeticao imediatamente anterior mediu 36,56 s, RSS 165.724 KiB e heap
observado 51.759 KiB. Contra o unico baseline, o RSS da repeticao final cresceu
aproximadamente 6,0%, enquanto a duracao permaneceu estavel; a variacao entre
execucoes mostra por que isso nao e um benchmark controlado. O heap registrado
e a maior amostra antes de uma coleta, nao pico exato. JMH foi adiado: neste
ponto, corpus e volume respondem as decisoes de produto, enquanto
microbenchmarks fariam mais sentido depois de o subscore lexical estabilizar e
com protocolo/hardware controlados.
