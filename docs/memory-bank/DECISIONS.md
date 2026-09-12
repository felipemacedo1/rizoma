# Decisoes arquiteturais

## 2026-09-10 - Nome Rizoma

O nome representa uma estrutura viva que parece simples na superficie e forma
conexoes complexas em profundidade.

## 2026-09-10 - Linguagem Java

Java e a linguagem definida para o produto. Na criacao do repositorio, a versao
ainda nao estava fechada e Java 21 ou superior era apenas a hipotese inicial.
Essa indefinicao foi substituida pela decisao de baseline registrada abaixo.

## 2026-09-10 - Autonomia confinada ao workspace

O Codex opera sem prompts rotineiros, mas permanece limitado ao workspace.
Decisoes irreversiveis, segredos e dados externos continuam protegidos.

## 2026-09-10 - Java 21 como baseline, CI 21 e 25

O projeto compilara com `--release 21`. Java 21 oferece o melhor equilibrio
entre recursos modernos finalizados, compatibilidade corporativa, ecossistema e
facilidade de contribuicao. Java 25 e o LTS mais novo e sera testado em CI. Uma
elevacao do baseline exige ADR e beneficio mensurado ou versao major.

## 2026-09-10 - Maven 3.9.x via Maven Wrapper

Maven foi escolhido por convencao, familiaridade corporativa e publicacao no
Maven Central. Maven 4 ainda nao e GA; o Wrapper fixara uma versao 3.9.x estavel.

## 2026-09-10 - Monolito modular com core independente

O produto nasce como biblioteca modular, nao como servico. O core nao depende
de Spring, POI, parser CSV, banco ou rede. CSV, Excel, locale pt-BR e CLI sao
adaptadores/modulos ao redor do core. Novos modulos so nascem com codigo util e
testes.

## 2026-09-10 - Dataset streaming e profiling limitado

`Dataset` sera cursor lazy e de passagem unica. Perfis usarao estruturas
limitadas e cada medida declarara se e exata, estimada, amostrada ou indisponivel.
O motor nao materializara datasets inteiros por conveniencia.

## 2026-09-10 - Score nao e probabilidade

Score, cobertura, margem, contradicoes e calibracao sao dimensoes separadas.
Antes de corpus rotulado, o valor sera chamado de confidence index e auto-map
ficara desligado por padrao.

## 2026-09-10 - Incrementos 0.1a e 0.1b

0.1a entrega um fluxo CSV completo e explicavel. 0.1b adiciona XLS/XLSX pelo
mesmo contrato; apenas a soma sera publicada como release 0.1. Isso concilia o
suporte minimo de formatos com uma primeira entrega vertical pequena.

## 2026-09-10 - Apache License 2.0 recomendada

Apache-2.0 foi escolhida por permissividade e concessao explicita de patentes.
A decisao foi executada em 2026-09-11 com o texto oficial e `NOTICE` proprio.

## 2026-09-11 - Contratos corretivos do 0.1a

CNPJ pode conter letras significativas e nao sera normalizado como somente
digitos. AnalysisResult registra fingerprints de origem, schema e configuracao
para permitir invalidar planos futuros. Naquele incremento, Jaro/Jaro-Winkler
permaneceu planejado para o 0.2, com a exigencia de documentar a complexidade da
implementacao real, incluindo o pior caso da busca em janela.

## 2026-09-11 - Contratos implementados no 0.1a

O schema JSON e o AnalysisResult usam `formatVersion` 1.0 e rejeitam
propriedades desconhecidas. Colunas usam identidade posicional `cN`. Um campo
destino aceita conjunto de tipos semanticos. Obrigatoriedade e preservada, mas
nao validada; exclusividade participa apenas da deteccao de conflitos.

Detectores recebem o valor bruto somente durante acumulacao e publicam apenas
agregados. As amostras sao sempre protegidas, independentemente do acerto do
detector. O fingerprint da origem e comparado antes e depois da analise.

Maven Wrapper foi fixado em 3.9.16. Dependencias diretas do 0.1a: Commons CSV
1.14.1, Commons IO 2.22.0, Jackson 2.21.6, Picocli 4.7.7 e JUnit 5.14.4. JaCoCo
0.8.15 aplica gates de 85% de linhas e 80% de branches no core.

Dice e Levenshtein sao um unico subscore lexical correlacionado. A
confiabilidade de evidencia de conteudo e `min(1, N/minEvidence) *
(1-ambiguous/N)`. Margem usa todos os candidatos elegiveis antes do top-K;
ausencia de evidencia causa abstencao; `AUTO_MAP` e falso no default do core.

## 2026-09-11 - Estrategia Excel do 0.1b

`rizoma-format-excel` usa Apache POI 5.5.1 sem acoplar o core ao POI. XLSX e
lido por StAX sobre a worksheet e passa antes por preflight ZIP com Commons
Compress 1.28.0. Fontes locais usam acesso aleatorio read-only; fontes nao
locais usam spool temporario limitado e removido. A tabela de shared strings do
POI ainda e materializada e esse risco e limitado por tamanho expandido.

XLS legado usa o user model HSSF com teto proprio default de 20 MiB. Adotar o
event model HSSF exigiria produtor concorrente ou spool intermediario para
oferecer o cursor sincrono do core; essa complexidade nao foi introduzida sem
evidencia de demanda. Formulas nunca sao avaliadas e usam politica `cached`,
`expression` ou `reject`.

O `AnalysisResult` passou a 1.1 para incluir atributos seguros de estrutura,
como indice da planilha e linha de inicio. O fingerprint de configuracao agora
inclui reader options e seed. `explain` continua lendo 1.0 e 1.1.

## 2026-09-11 - Hardening da candidata 0.1

A compatibilidade de leitura de relatorio 1.0 e uma garantia de regressao da
CLI e possui teste dedicado; o core continua produzindo somente 1.1. XLSX com
qualquer relacionamento externo e rejeitado a partir dos metadados OPC antes da
worksheet, sem resolver o alvo. O teste usa listener loopback sem resposta e
exige zero conexoes, evitando dependencia de rede externa.

Fixtures versionadas devem ser sinteticas, pequenas e sem vinculo com pessoas
ou sistemas reais. CPFs com checksum valido sao permitidos somente quando
necessarios para o detector, acompanhados de nomes genericos, dominios
reservados e documentacao explicita de proveniencia.

Documentos de contribuicao, conduta, seguranca e changelog passam a fazer parte
do gate da primeira release. Como o reporte privado do GitHub esta desabilitado,
`SECURITY.md` nao inventa um e-mail nem promete SLA; orienta contato privado do
mantenedor e um fallback publico sem detalhes exploraveis.

## 2026-09-11 - Profiling e avaliacao do 0.2

O perfil guarda frequencias exatas ate 1.024 valores distintos. Apos o limite,
cardinalidade usa HyperLogLog com `p=10` e erro relativo esperado de 3,25%;
top-K usa Space-Saving default 10. Entropia e exata com a tabela completa e
estimada por intervalo depois da transicao. Toda aproximacao declara
`ESTIMATED`, metodo e erro; valores publicos continuam mascarados.

Dice, Jaccard, Levenshtein, Jaro, Jaro-Winkler, trigram Dice e trigram cosine
formam um unico subscore lexical em tres grupos correlacionados. O baseline
Dice/Levenshtein 0.1 permanece selecionavel apenas para avaliacao reproduzivel.
Pesos finais nao foram calibrados nem ajustados para eliminar a falha do corpus.

Schemas com ate 128 campos avaliam todos os pares. Acima disso, pruning por
alias/tokens/semantica/tipo mantem aliases exatos e 32 candidatos nao exatos,
registrando ate 100 exclusoes por coluna e avisando o total quando truncado.
Nao foi criado indice complexo ou matching global.

O corpus sintetico passa a ser o gate objetivo de ranking e abstencao. JMH foi
adiado: corpus e volume ja respondem as decisoes atuais, enquanto o subscore
ainda pode mudar. O bloqueio remoto de billing e pendencia operacional e nao
impede desenvolvimento local verificado.

## 2026-09-11 - Plano e dry run sem destino no 0.3

`MappingPlan` 1.0 nasce experimental e exige mappings explicitamente
confirmados. Ele carrega identidades/fingerprints de fonte, schema e
configuracao, incluindo o registro de transformers/validators. Dry run rejeita
divergencias e planos que deixem campo required sem mapping.

O pipeline de transformacao e validacao pertence ao core e trabalha por linha;
o relatorio retem somente contagens e amostras mascaradas limitadas. A API de
dry run nao recebe sink. Uma porta de destino nao foi criada no 0.3 para tornar
a ausencia de escrita uma propriedade estrutural, nao uma convencao.

Transformers e validators usam IDs/versoes explicitos. Erro de dado e resultado
tipado; excecao indica configuracao ou execucao invalida. Datas e decimais
exigem contexto suficiente, e identificadores TEXT nunca sofrem conversao
numerica implicita. Unique/FK e CNPJ permanecem ausentes ate existir contrato
correto e custo controlado.

## 2026-09-11 - Feedback historico deterministico no 0.4

Feedback e evento explicito e imutavel, nunca efeito colateral de `analyze` ou
`dryRun`. `CONFIRMED` adiciona evidencia positiva, `REJECTED` negativa e
`CORRECTED` negativa para o sugerido mais positiva para o escolhido. Celulas e
headers originais nao entram no evento; somente identidade normalizada e
metadados estruturais seguros sao persistidos.

O default e `NoOpMappingKnowledgeBase`, preservando resultados anteriores. A
knowledge pertence ao `AnalysisRequest`; o engine captura um snapshot imutavel
uma vez e faz lookup indexado por schema/versao/fingerprint, target, nome
normalizado, locale e contexto. Nao existe estado historico global oculto.

O suporte usa `(P-N)/(P+N+2)`, saturacao logaritmica em oito eventos e
confiabilidade limitada. A direcao historica e score 0/0,5/1, separada da
confiabilidade, sem semantica probabilistica. Peso default 0,10 e contradicao
semantica forte pode tornar o candidato inelegivel. Decay temporal foi adiado;
timestamps servem somente para auditoria nesta fase.

`AnalysisResult` evolui para 1.3 e `MappingPlan` para 1.1, ambos registrando ID e
versao do snapshot. O snapshot participa do `planId`; conhecimento posterior
nao modifica plano. O adapter JSON Lines fica no CLI para manter Jackson fora
do core, com limites, validacao estrita, append/lock e deteccao de truncamento.
Ele e local/cooperativo, nao substitui um banco transacional multi-host.

## 2026-09-12 - Adaptive Layout & Data Projection no 0.5

Matching global/Hungarian foi retirado da prioridade 0.5 e ficou
`DEFERRED / EVIDENCE-DRIVEN`. Importacao real admite source ignorada,
constante, N->1 derived e reutilizacao 1->N; assignment one-to-one nao e o
modelo central sem evidencia de colisoes globais relevantes.

Fingerprint de conteudo e signature de layout sao identidades distintas. O
primeiro continua vinculando cada `MappingPlan` aos bytes atuais. A segunda usa
somente metadados estruturais protegidos e guard limitado. `LayoutTemplate` e
receita operacional confirmada e imutavel; knowledge continua sendo evidencia
historica. Template nunca e plano reutilizado: a instanciacao sempre cria
`MappingPlan` 1.2 com fingerprint da fonte corrente.

As rotas sao enums auditaveis, nao probabilidades: `FULL_ANALYSIS`,
`FAST_REUSE` e `ADAPTIVE_REANALYSIS`. Fast aceita estrutura igual ou reorder
inequivoco depois de checar schema/config/versoes/tipo/semantica. Adaptive cobre
adicao irrelevante ou uma renomeacao localizada que passe guard lexical ou
semantico. Dependencia removida, duplicidade nova, tipo ou semantica
contraditorios escalam para full. O guard default observa 64 linhas e nao
promete detectar drift raro.

Projection pertence ao plano orientado ao target e possui somente
`SOURCE_COLUMN`, `CONSTANT`, `DERIVED` e `UNMAPPED`; source ignorada fica em
colecao separada de no-match. Derived referencia source columns/constantes e
oferece seis operacoes fechadas com `BigDecimal`, sem scripting, eval ou DAG de
targets. Registry persistente foi adiado; NoOp e o default, InMemory e limitado
e indexado, e a CLI carrega template explicito por execucao.

## 2026-09-12 - Public Java API & Adoption Layer no 0.6

O caso comum passa a depender de um unico artefato agregador `rizoma`. A
fachada experimental `Rizoma` compoe os adapters CSV/XLS/XLSX, regras core e
pt-BR, limites seguros e stores NoOp. Ela nao duplica o pipeline: Simple API,
Workflow API e Extension API sao camadas de entrada sobre o mesmo engine.

`process()` nunca confirma uma sugestao. Sem plano ou template confirmado,
retorna `REVIEW_REQUIRED`; com plano source-bound, valida e executa dry run;
com template, usa as guardas FULL/FAST/ADAPTIVE do 0.5. Dados invalidos viram
`INVALID`, enquanto falhas tecnicas usam `FAILED` ou uma hierarquia publica
pequena na Workflow API.

`Path` e a fonte preferida para volume. `byte[]` recebe copia defensiva e
`InputStream` one-shot e materializado em memoria sob limite explicito porque
fingerprint e pipeline exigem reabertura; nao ha temporario oculto. A API 0.x
nao promete compatibilidade binaria nem concorrencia geral. A fachada e
reutilizavel sequencialmente; uso concorrente depende de todas as extensions e
stores fornecidos pelo integrador.

## 2026-09-12 - Namespace publico controlado antes da primeira release

O `groupId` permanece `io.github.felipemacedo1` e todos os packages Java passam
a usar a raiz `io.github.felipemacedo1.rizoma`. O namespace anterior
`io.github.rizoma` foi removido integralmente, sem aliases ou classes-ponte.

A convencao Java recomenda uma raiz derivada de dominio/namespace controlado, e
o Maven Central provisiona `io.github.<github-user>` como namespace verificavel
para a conta correspondente. `io.github.rizoma` nao expressava um namespace
controlado pelo projeto e divergia do `groupId`. Fontes oficiais consultadas:
<https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html> e
<https://central.sonatype.org/register/namespace/>.

A mudanca e deliberadamente breaking, mas ocorre antes de tag, release,
publicacao ou consumidor externo conhecido. Fazer a migracao agora evita uma
quebra muito mais cara depois da adocao. ArtifactIds e contratos JSON nao
mudam; nomes de classe serializados nunca fizeram parte dos contratos.
