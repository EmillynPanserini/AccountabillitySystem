# Plano de desenvolvimento — Sistema de Contabilidade

**Visão funcional e roadmap da arquitetura v1.5.** A [especificação técnica canônica](../planejamento.md) define contratos Java, PostgreSQL, REST, Angular, testes e critérios. Este documento apresenta os objetivos, a sequência das entregas e as decisões de produto. A revisão documental não implica aprovação técnica nem autorização de implementação.

## 1. Visão geral

Registrar fatos contábeis por empresa com partidas dobradas, autoria, integridade transacional e estorno rastreável. A interface web utiliza **Angular com TypeScript**. O backend preserva a base Java 21/Spring Boot 3.5.7 e usa PostgreSQL 17. Roteamento, formulários e serviços Angular seguem [F.3–F.4 do plano técnico](../planejamento.md#f3-frontend-angular-e-typescript).

## 2. Diagnóstico e evidências

A base auditada `3c7d398` já tem inicialização Spring Boot, `GET /api/status` textual e configuração PostgreSQL em Compose. As capacidades contábeis são planejadas. Banco, volumes históricos, anexo integral e referência visual permanecem sem verificação. O diagnóstico e o procedimento de preservação estão no [plano técnico](../planejamento.md#parte-a--diagnóstico-do-projeto-existente).

## 3. Requisitos funcionais e não funcionais

| ID | Requisito funcional | Evidência de aceite esperada |
| --- | --- | --- |
| RF01 | Cadastrar, editar e selecionar empresas autorizadas | Usuário só acessa empresas vinculadas; troca de contexto atualiza todas as telas. |
| RF02 | Plano manual, modelo padrão e Excel | Hierarquia validada; importação prévia; exportação reimportável sem perda semântica. |
| RF03 | Balancete inicial e abertura | Saldos sujeitos a segregação de criador e autores efetivos, aprovados por versão, digest e selo, geram lançamento equilibrado e rastreável. Substituição autorizada preserva o histórico e uma única posição ativa. |
| RF04 | Lançamentos compostos, histórico e estornos | Débitos iguais a créditos; nenhum registro contabilizado é excluído fisicamente. |
| RF05 | Vendas consolidadas por período | Total reconciliado às fontes, empresa e política contábil aprovada. |
| RF06 | Vendas por Excel/CSV | Prévia, erros por linha/campo, duplicidades e confirmação idempotente. Abertura via H3 usa rascunho existente e preserva autoria; recuperação de jobs exige titular vigente. |
| RF07 | Diário | Lançamentos completos, filtros e totais preservados por emissão. |
| RF08 | Razão | Saldo anterior, movimentos e saldo final reconciliados por conta. |
| RF09 | Balancete | Totais equilibrados e subtotais sem duplicar analíticas. |
| RF10 | Balanço Patrimonial | Ativo = passivo + PL, com resultado uma vez. |
| RF11 | DRE | Resultado por competência mantido após encerramento técnico. |
| RF12 | DFC | Políticas publicadas por vigência, classificações por revisão e emissão reproduzível, com reconciliação do caixa. Projeção dinâmica e alocações editáveis têm contratos distintos. |
| RF13 | PDF e Excel | Exportação coincide com consulta e identifica empresa, filtros e geração. |
| RF14 | Interface web | Fluxos completos, responsivos e acessíveis, validados contra referências disponíveis. |
| RF15 | Autenticação, autorização e auditoria | Tentativas entre empresas negadas; eventos sensíveis rastreáveis, incluindo autoria material, executor técnico e recuperação terminal negada. |
| RF16 | Gerir calendário, reabertura e encerramento anual | Sem sobreposição, escrituração só em estado elegível, encerramento único, com reversão apenas no fluxo anual autorizado. |

| ID | Requisito não funcional |
|---|---|
| RN01 | Exatidão monetária e arredondamento declarado. |
| RN02 | Isolamento por empresa, permissão por estado, transação e revogação. |
| RN03 | Integridade, trilha, imutabilidade e efeito único por origem. |
| RN04 | CI, compatibilidade e evidência de testes. |
| RN05 | Preservação do banco, migrações e recuperação verificáveis. |
| RN06 | Operação, consistência de emissões e rastreabilidade da interface. |

Requisitos não funcionais obrigatórios: `BigDecimal` de ponta a ponta; operações financeiras transacionais; isolamento por empresa; integridade referencial; testes unitários e de integração; migrações Flyway; nenhum segredo em código ou logs. Propostas: BRL inicialmente, valores persistidos em `numeric(19,2)`, rejeição de casas excedentes em vez de arredondamento silencioso; cálculos intermediários com maior precisão e arredondamento explicitamente aprovado. APIs devem serializar valores decimais como strings para evitar perda no JavaScript.

Definir antes de testes de carga: volume de empresas, usuários simultâneos, lançamentos/ano, tamanho máximo de arquivos, retenção, metas de latência, disponibilidade, RPO e RTO. Não há SLA aprovado. Proposta de benchmark: relatórios anuais com massa representativa, registrando tempo, memória e planos de execução; fixar limites de aceite antes da homologação.

## 4. Roadmap funcional

O [backlog canônico H.2](../planejamento.md#h2-backlog-rastreável) é a fonte para IDs, arquivos, critérios, dependências e testes. A execução de cada incremento exige autorização própria.

| Fase | Resultado para o projeto | Tarefas canônicas |
|---|---|---|
| F0 · Escopo e homologação | Evidências, toolchain, políticas contábeis e identidade têm critérios e dependências próprios. | T01, T02, T29 e T30 |
| F1 · Fundação | CI Java/Angular após homologar a toolchain de T02, sem depender de políticas contábeis, cadeia sintética isolada, bootstrap, sessão, empresa, vínculo e auditoria após a decisão de identidade. T04 é o levantamento e a preparação autorizada do caminho de legado. | T03–T07 |
| F2 · Plano e calendário | Contas e modelos, calendário coerente, parsing compartilhado antes dos importadores. Recuperação interna revalida solicitante e titular sob bloqueio. | T08–T11 |
| F3 · Escrituração | Rascunho composto, contabilização atômica, estorno e fechamento/reabertura concorrentes. | T12–T14 |
| F4 · Abertura e livros | Posição inicial aprovada com segregação por autoria efetiva e selo vinculado à revisão/digest e substituível atomicamente após as proteções do motor; Diário, Razão e Balancete. | T15–T17 |
| F5 · Vendas | Lote produz fontes e consolidação aprovada usa o motor, com deduplicação por origem. | T18–T19 |
| F6 · Demonstrações | Políticas e classificações versionadas, Balanço, DRE e DFC direta reconciliada e reproduzível. | T20–T21 |
| F7 · Encerramento e exportações | Encerramento e reversão anuais exclusivos têm tarefa própria, PDF/XLSX da emissão, recuperação de exportação condicionada à autorização vigente do titular original e DFC indireta quando homologada. | T22–T25 |
| F8 · Integração e homologação | Fluxos acessíveis, carga, recuperação e evidência de implantação. | T26–T27 |
| Trilha opcional de legado | Adoção por comparação e reconciliação em cópia isolada, sujeita a autorização própria. | T28, após T04 e T05 |

## 5. Decisões e condições para avanço

- Angular com TypeScript é a decisão de frontend. Versões compatíveis, bibliotecas complementares e estratégia de implantação permanecem a validar.
- O responsável contábil precisa validar perfil normativo, reconhecimento e tributos, abertura, períodos, exercício, classificação de DFC e retificação. Operação precisa validar retenção, desempenho, RPO/RTO e estratégia de identidade.
- A eventual adoção de Flyway sobre dados existentes segue [D.5](../planejamento.md#d5-flyway-e-banco-existente), com identificação da origem e do destino, autorização própria e cópia restaurada em isolamento.
- O anexo integral e a referência visual ainda precisam ser fornecidos. Alternativamente, o usuário pode validar o inventário substituto da [seção F.4](../planejamento.md#f4-inventário-das-telas-angular). M03 permanece bloqueado.
- A [matriz I.4](../planejamento.md#i4-matriz-de-resolução-da-auditoria-da-v10) registra os 18 achados e seus testes. A [matriz I.5](../planejamento.md#i5-matriz-dos-novos-achados-da-segunda-auditoria) trata N01–N03 separadamente. A nova auditoria depende de autorização.

## 6. Revisões documentais v1.3 a v1.5

O técnico canônico permanece em [planejamento.md](../planejamento.md). As matrizes I.4–I.6 preservam os 21 IDs históricos e registram os 12 achados da terceira auditoria. A base v1.3 especificou os deltas históricos abaixo. I.8 reúne os oito achados da quarta auditoria e I.9 reavalia os 33 anteriores. Testes da aplicação e homologação contábil ficam em etapas próprias.

| Tarefa | Resultado documental e referência | Dependência |
|---|---|---|
| V13-D01 | Reserva e reautorização na transação do efeito. Ordem e modo de locks por escritor, fronteira de privilégios, releitura e flush, em E.3 e G.6. | Base |
| V13-D02 | Ocorrências de fechamento, prévias revisáveis, aprovação e reversão anual exclusivas, em E.4, E.9 e F.2. | D01; homologação anual antes da ativação |
| V13-D03 | Invalidação da aprovação, dois pares período/data e posição ativa única. Vigência DFC, pareamento interno e emissão congelada, em E.6/E.8/E.9. | D01; homologação de abertura/DFC antes da ativação |
| V13-D04 | Posse por geração, hora após lock, referência privada até commit, recuperação e retenção de origens, em G.1/G.6. | D01 |
| V13-D05 | Assinaturas, tipos e contexto persistente, consultas de seletores/aprovadores e vigência no XLSX reimportável, em C, D.2 e F. | D01–D04 nas interfaces afetadas |
| V13-D06 | Fixtures alcançáveis e gates por capacidade em G.4, H.2 e I.4–I.7. | D01–D05 |
| V13-D07 | Conferência independente das matrizes, contratos, regressões, diferenças Git e escopo. Evidências na conversa da equipe. | D06 |

**Sequência de entrega.** T06 separa bootstrap e sessão de provisionamento e autorização concorrente. A concessão de vínculo ponta a ponta é verificada em T07. T22 separa prévia, execução e reabertura como aceites, mantendo cada transação crítica íntegra. T21 depende da substituição definida em T15 para verificar os cortes de DFC. T26 depende de T23 quando a indireta integrar o escopo. T27 verifica a homologação final somente depois das capacidades do escopo. T04/T28 mantêm autorização própria de legado.

**Telas.** Calendário recupera IDs e versões para um segundo aprovador e distingue ocorrência e prévia obsoletas. Aberturas exibem invalidação de aprovação e os pares período/data. DFC usa método explícito e informa lacunas de pareamento e vigência. Consultas limitadas e autorizadas sustentam seletores de layouts, regras, políticas e identidades; contas preservam vigência no fluxo importação/exportação. Angular com TypeScript permanece exclusivo.

**Pendências.** Usuário decide identidade, delegação, método indireto, recuperação, retenção e M03. Contador habilitado valida datas, contas históricas, dependências entre exercícios e tratamento de abertura na DFC. A prova executável da concorrência, rollback, leases e contratos será feita após implementação autorizada em ambiente isolado. A próxima auditoria requer autorização própria.

### Delta direcionado v1.4

| Tarefa | Resultado documental e referência | Dependência |
|---|---|---|
| V14-D01 | C/D/E/F/G/H. Encerramento com e sem efeito, ocorrência anual e revisão. | Base v1.3; T29 antes da ativação contábil. |
| V14-D02 | D.2/E.9/G.1/G.6. Varredura do último lease, reautorização e recuperação em geração posterior. | Base v1.3. |
| V14-D03 | C.3/D.2/E.7/F/G.6 H3. Upload congela contexto de abertura e confirmação só altera rascunho. | Base v1.3; NOVA desabilitado até decisão. |
| V14-D04 | E.8/F/G.6. Uma leitura REST de revisão DFC, confirmação de snapshot e divulgação reautorizada. | Base v1.3. |
| V14-D05 | E.3/E.5/F/G.6. Chave global na criação de empresas e lock estrutural de contas. | Base v1.3. |
| V14-D06 | G.4/H.2/I.8–I.9. Gates por capacidade e rastreabilidade de 41 IDs. | D01–D05. |
| V14-D07 | Conferência independente dos 41 IDs, protocolos, contratos, escopo e diff. Evidências na conversa. | D06. |

T06 verifica criação idempotente de empresa, T11 reserva de upload, T13 replay e rollback de lançamento e T19 unicidade de origem de vendas. T15 verifica o alvo já existente de abertura. T22 verifica calendário anual e trilha também no caminho sem efeito. T10 e T24 verificam esgotamento após reinicialização. T16/T21/T24 separam consistência da emissão e autorização de acesso.

**Telas Angular com TypeScript na v1.4.** Períodos exibe modo e ocorrência anual. Importações exibe alvo, corte e versão congelados. DFC/Políticas recupera revisão e ETag. Empresas reaproveita a chave em retry. Contas distingue campos estruturais de nome e da ação de inativação. Os estados de falha existentes atendem ao esgotamento de jobs.

**Decisões externas.** O ramo NOVA fica desabilitado. M03 continua não verificável. Validar com contador datas, contas históricas, dependências interexercícios e efeitos na abertura e na DFC. Operação deve aprovar varredura, retenção e limites de materialização. Ensaios de PostgreSQL/JPA/API/Angular e storage ficam na implementação futura autorizada.

### Delta focal v1.5

| Tarefa | Achado e resultado documental | Aceite e dependências |
|---|---|---|
| V15-D01 | V14-N01. F2/T10 e F7/T24 recuperam jobs terminais sob empresa S → vínculos S ordenados → job X, com revalidação do solicitante e do titular original após a espera e hora do banco. Titular inelegível mantém falha e gera rejeição auditada. | PG-ultimo-lease e PG-ultimo-lease-exportacao verificam titular vigente/revogado, aquisição e revogação nas duas ordens, rollback e impossibilidade de publicar com posse vencida. T10 usa fixture própria sem T11. T24 mantém T16/T21/T07/T10. |
| V15-D02 | V14-N02. F4/T15 distingue criador, autores humanos, solicitante/confirmador H3 e executor técnico. Conserva autoria após sobrescrita, exclusão ou edição superficial e requer selo exato de revisão/digest/autores. Editar posição aprovada invalida o selo. | PG-abertura-autoria, PG-abertura, PG-alvo-abertura e PG-substituir verificam papéis acumulados, concorrência, aprovação independente e rollback. T15 mantém T13/T14/T10/T29. |
| V15-D03 | V14-N03. F6/T21 usa GET de classificação como projeção dinâmica e GET de alocações como origem do ETag forte do PUT no mesmo recurso. Criação condicional e reclassificação distinguem 400/412/428 e conflito 409. Replay requer autorização vigente e não repete o efeito. Repetir a criação com alvo existente devolve 412 e recuperação pelo GET editável. Repetir atualização devolve 200 sem ETag somente enquanto a revisão produzida está vigente, ou 412 após revisão sucessora. | CT-obterClassificacaoAtual, CT-obterAlocacaoAtual, CT-gravarAlocacao e PG-dfc-leitura-atual cobrem recarga, permissões, concorrência, revogação, replay e snapshots anteriores. T21 mantém T20/T16/T15. |
| V15-D04 | Preservar os 41 IDs e os pareceres históricos. Acrescentar V14-N01, V14-N02 e V14-N03 na matriz canônica. | I.4–I.10 totalizam 44 IDs distintos. Conferir regressões dos 14 controles históricos e dos cinco resolvidos pela quinta auditoria. M03 continua não verificável. |

**Telas Angular.** Aberturas mostra versão, revisão, digest, proveniência autorizada e invalidação do selo. Importações conserva o alvo H3 mesmo na prévia vazia. Importações e Exportações exibem falha autorizada; a recuperação é interna. DFC e Políticas consultam a projeção dinâmica; o formulário lê o recurso editável na abertura/recarga. Receber 412 exige atualizar as leituras e reconfirmar a intenção.

**Pendências.** NOVA e transferência de titularidade continuam desabilitadas. Produto e operação ainda definem identidade/delegação, canal e política de recuperação, cadência, orçamento, retenção e limites de jobs/snapshots. A homologação contábil cobre datas, cortes, contas históricas, dependências entre exercícios e abertura/indireta na DFC. M03 depende de referência acessível ou aceite expresso. T02, T03 e T05 estão fora da autorização focal. Verificação documental e posterior auditoria focal são etapas próprias; ensaios PostgreSQL/JPA, armazenamento, HTTP e Angular permanecem futuros.
