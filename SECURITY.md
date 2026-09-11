# Politica de Seguranca

O Rizoma processa arquivos potencialmente hostis e dados que podem conter PII.
Relatos responsaveis ajudam a proteger usuarios e datasets.

## Versoes suportadas

Antes da primeira release, somente a candidata presente em `main` recebe
correcoes. Depois da v0.1.0, a linha `0.1.x` sera suportada ate que uma politica
de versoes mais ampla seja publicada.

| Versao | Suporte |
|---|---|
| `main` / candidata nao publicada | Sim |
| `0.1.x` | A partir da v0.1.0 |
| Versoes anteriores | Nao aplicavel |

## Como relatar uma vulnerabilidade

O reporte privado de vulnerabilidades do GitHub ainda nao esta habilitado neste
repositorio. Entre em contato com o mantenedor por um canal privado publicado em
seu [perfil no GitHub](https://github.com/felipemacedo1).

Se nenhum canal privado estiver disponivel, abra uma issue contendo apenas:

- que voce encontrou uma possivel vulnerabilidade;
- componente e versao afetados;
- um pedido de canal privado para os detalhes.

Nao publique exploit, arquivo malicioso, dados pessoais, tokens, caminhos
internos ou detalhes que permitam reproducao antes do contato privado.

No relato privado, informe impacto, pre-condicoes, passos minimos de reproducao
e mitigacao conhecida. Use fixtures sinteticas. Nao envie arquivos de terceiros
sem autorizacao.

Nao prometemos SLA antes da primeira release. O mantenedor deve confirmar o
recebimento quando possivel, investigar sem expor o reportante e coordenar a
correcao e a divulgacao responsavel.

## Escopo prioritario

- execucao ou avaliacao indevida de formulas/macros;
- ZIP bombs, XML external entities e relacionamentos externos;
- bypass de limites de memoria, bytes ou registros;
- path traversal e temporarios nao removidos;
- vazamento de celulas brutas em logs, erros ou relatorios;
- alteracao da fonte ou escrita em destino durante analise;
- dependencias com vulnerabilidades exploraveis no fluxo padrao.

O Rizoma nao executa formulas, nao carrega relacionamentos externos, nao envia
celulas pela rede e nao escreve em sistemas de destino no escopo atual. Consulte
o README para limites conhecidos; eles nao devem ser interpretados como garantia
contra toda classe futura de arquivo hostil.
