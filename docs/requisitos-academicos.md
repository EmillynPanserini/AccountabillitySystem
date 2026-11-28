# Requisitos e critérios de aceite

Este arquivo reúne requisitos comprovados do Sistema de Contabilidade. Não foi localizado enunciado acadêmico próprio, rubrica de avaliação ou prazo institucional: não atribuir exigências a uma instituição sem fonte. A [referência Lex](https://github.com/HsFenrir/lex/blob/main/docs/requisitos-academicos.md) inspira apenas a organização, sem importar requisitos, tecnologias ou arquitetura.

## Fontes e natureza

- **U:** solicitações expressas do usuário, consolidadas na transição v1.1 de 27/11/2026: tecnologias, arquitetura simples, segurança, testes e prioridade T06. Regras de desenvolvimento estão em [AGENTS.md](../AGENTS.md).
- **I:** identidade e autorização homologadas em T30, registradas na [retomada de 27/11](RETOMADA-2026-11-27.md), seção “T30 homologada”. Decisões operacionais vigentes estão em [decisões e cronograma](decisoes-e-cronograma.md).
- **F:** escopo funcional documentado em [PLANO_DESENVOLVIMENTO.md](PLANO_DESENVOLVIMENTO.md), seção 3, e `75285c9:planejamento.md`, H.2. É requisito de produto documentado, não comprovação acadêmica nem autorização de implementação integral; políticas pendentes continuam condicionantes.
- **C:** comportamento existente comprovável por código, configuração e testes versionados. A presença do teste não comprova execução nesta sessão; resultados ficam no [andamento](andamento.md).

## Funcionalidades e aceite

Os IDs RF preservam a referência funcional; a relação com as tarefas está na v1.5 indicada no cronograma. Aceite descreve o resultado a verificar após implementação autorizada.

| ID | Requisito / natureza e fonte | Critério de aceite |
|---|---|---|
| RF01 | Empresas e vínculos — produto, F; criação/isolamento aprovados, U/I | Criação atômica da empresa e vínculo fundador; replay sem duplicar; consulta/listagem somente autorizada; rollback sem empresa órfã. |
| RF02 | Plano de contas manual, modelo e Excel — produto, F | Hierarquia válida, contas da mesma empresa, rejeição de ciclos e importação/exportação sem perda de código ou estrutura. |
| RF03 | Abertura e saldos iniciais — produto, F | Aprovação vinculada à versão e autoria, lançamento equilibrado e rastreável, sem duplicidade nem confirmação parcial; políticas de corte homologadas. |
| RF04 | Lançamentos compostos e estornos — produto, F | Rejeitar desequilíbrio e valores inválidos; contabilizar atomicamente; impedir edição de fato contabilizado e vincular estorno ao original. |
| RF05 | Vendas consolidadas — produto, F | Total reconciliado às fontes da empresa; mesma origem não produz novo efeito; política contábil aprovada. |
| RF06 | Importação de vendas Excel/CSV — produto, F | Prévia e erros por linha/campo; rejeição de duplicidade; confirmação idempotente e rollback integral em falha. |
| RF07 | Diário — produto, F | Lançamentos completos, filtros autorizados e totais consistentes com a escrituração. |
| RF08 | Razão — produto, F | Saldo anterior, movimentos e saldo final reconciliados por conta e empresa. |
| RF09 | Balancete — produto, F | Débitos/créditos e saldos reconciliados sem dupla contagem de contas sintéticas. |
| RF10 | Balanço Patrimonial — produto, F | Equação patrimonial e classificações conferidas por massa independente e política homologada. |
| RF11 | DRE — produto, F | Resultado reconciliado, sem dupla contagem ou zeramento indevido por encerramento. |
| RF12 | DFC — produto, F | Variação de caixa reconciliada, transferências internas excluídas e classificações rastreáveis; método indireto depende de homologação. |
| RF13 | Exportação PDF/Excel — produto, F | Conteúdo coincide com a emissão consultada, identifica filtros/empresa e exige autorização vigente no download. |
| RF14 | Interface Angular — aprovado, U; alcance funcional, F | Fluxo pertinente completo, responsivo e utilizável por teclado; troca de empresa não mostra dados do contexto anterior. Equivalência visual depende de referência validada. |
| RF15 | Identidade, sessão e autorização — aprovado, U/I | Login real, logout e expiração invalidam acesso; CSRF rejeita mutações indevidas; bootstrap concorrente cria um administrador; convites, delegação e isolamento passam casos positivos e negativos conforme decisões T30. |
| RF16 | Calendário, fechamento e reabertura — produto, v1.5 H.2 | Datas e períodos coerentes, sem sobreposição; escrituração respeita fechamento e concorrência; reabertura autorizada e rastreável. |

## Requisitos transversais

| ID | Requisito / fonte | Critério de aceite |
|---|---|---|
| RN01 | Precisão e integridade — U/F | Valores monetários sem ponto flutuante; escala explícita; limites, arredondamentos, partidas dobradas e rollback verificados. |
| RN02 | Segurança e isolamento — U/I | Operações autorizadas no backend; negativas entre empresas, elevação indevida e credenciais inválidas cobertas, sem segredos em logs. |
| RN03 | Rastreabilidade — U/F | Origem, ator e alterações recuperáveis; falha não deixa efeitos nem trilha transacional parcial. |
| RN04 | Qualidade e reprodução — U | Incremento funcional com testes relevantes executados e resultados verificáveis; regras técnicas referenciadas em AGENTS.md. |
| RN05 | Persistência e recuperação — U/F | Migrações apenas em destino isolado e autorizado; repetição, divergência e rollback testados; legado depende de autorização própria. |
| RN06 | Consistência e operação — F | Relatórios reconciliados e prontidão distinguível do status textual; metas de carga/recuperação só cobradas após aprovação. |

## Contratos já presentes e limites

- **C:** [StatusController](../src/main/java/org/example/StatusController.java) e [teste](../src/test/java/org/example/StatusControllerTest.java) definem `GET /api/status`, HTTP 200, `text/plain`, corpo `AccountabilitySystem funcionando!`; não é prova de saúde do banco.
- **C:** [Main](../src/main/java/org/example/Main.java), [configuração padrão](../src/main/resources/application.properties) e fundação T05 delimitam persistência sintética opt-in. Procedimentos estão no [README](../README.md).
- Não há aceite acadêmico comprovado, SLA aprovado ou implementação contábil inferível da documentação. Pendências de produto e ordem de execução ficam no cronograma; estado e evidências ficam no andamento.
