# Plano de desenvolvimento — Sistema de Contabilidade

Data: 04/11/2026. Status: planejamento para aprovação. Este documento não autoriza implementação, instalação de dependências, execução de serviços ou alteração de banco. Tecnologias existentes e regras solicitadas são premissas; escolhas adicionais abaixo são propostas, sujeitas às decisões da seção 17.

## 1. Visão geral e objetivos

Desenvolver um sistema web para escrituração e demonstrações de múltiplas empresas, com precisão monetária, partidas dobradas, rastreabilidade e controle de acesso por empresa. Cada relatório deve permitir chegar aos lançamentos e documentos que compõem seus valores.

O escopo compreende empresas, plano de contas, abertura, lançamentos e estornos, vendas consolidadas, importações Excel/CSV, Diário, Razão, Balancete, Balanço Patrimonial, DRE, DFC e exportações PDF/Excel. Consolidação de vendas significa agregação de operações de uma empresa; não significa consolidação societária entre empresas.

Referência funcional indicada: https://sistemadecontabilidade.netlify.app/. A tentativa de acesso nesta análise não retornou conteúdo utilizável. Não foram disponibilizadas telas nesta conversa nem encontrados arquivos de referência no repositório. A equivalência de navegação e funcionalidades dependerá de inventário posterior das telas, sem copiar código, marca ou identidade visual. Não se presume comportamento observado nessa referência.

## 2. Diagnóstico do repositório

Foram lidos integralmente `AGENTS.md`, `pom.xml`, `README.md`, `compose.yaml`, `Dockerfile`, `.gitignore`, os dois arquivos Java e `application.properties`; também foram inspecionados a estrutura e o histórico Git. Nenhum conteúdo de `.env` foi consultado.

| Evidência existente | Situação e consequência |
| --- | --- |
| Java 21, Spring Boot 3.5.7, Maven | Base técnica declarada no POM; não foi executado build nesta tarefa documental. |
| Spring Web, Data JPA, driver PostgreSQL, starter de testes | Dependências disponíveis; presença não comprova funcionalidades implementadas. |
| `org.example.Main` | Inicialização e escaneamento dos componentes. |
| `StatusController` | Apenas `GET /api/status`, retornando texto; não verifica saúde do banco. |
| `src/main/resources/application.properties` | DataSource por ambiente, `ddl-auto=update`, SQL habilitado e Open Session in View desabilitado. |
| `compose.yaml` | PostgreSQL 17 Alpine, aplicação, volume persistente e dependência de healthcheck. |
| `Dockerfile` | Build Maven e execução Java 21; empacotamento pula testes. Validação deverá ocorrer antes da imagem. |
| `src/test/java` | Diretório sem testes encontrados. |
| README | Contém apenas o título do projeto. |
| Histórico Git | `Initial commit` e duas mensagens `Forçando build no pipeline`; não demonstra Conventional Commits. Não foi encontrado arquivo de pipeline no inventário. |

Não há entidades, repositórios de domínio, migrações, Spring Security, frontend, importadores, relatórios ou exportadores implementados. O estado real do banco é desconhecido: não foi conectado, inspecionado ou alterado. `.codex/` e `AGENTS.md` já apareciam como não rastreados no início; devem permanecer preservados.

## 3. Requisitos funcionais e não funcionais

| ID | Requisito funcional | Evidência de aceite esperada |
| --- | --- | --- |
| RF01 | Cadastrar, editar e selecionar empresas autorizadas | Usuário só acessa empresas vinculadas; troca de contexto atualiza todas as telas. |
| RF02 | Plano manual, modelo padrão e Excel | Hierarquia validada; importação prévia; exportação reimportável sem perda semântica. |
| RF03 | Balancete inicial e abertura | Saldos aprovados geram lançamento equilibrado e rastreável. |
| RF04 | Lançamentos compostos, histórico e estornos | Débitos iguais a créditos; nenhum registro contabilizado é excluído fisicamente. |
| RF05 | Vendas consolidadas por período | Total reconciliado às fontes, empresa e política contábil aprovada. |
| RF06 | Vendas por Excel/CSV | Prévia, erros por linha/campo, duplicidades e confirmação idempotente. |
| RF07–09 | Diário, Razão e Balancete | Filtros por empresa/período, saldos verificáveis e ligação ao lançamento. |
| RF10–12 | BP, DRE e DFC | Regras versionadas e reconciliação com livros e caixa. |
| RF13 | PDF e Excel | Exportação coincide com consulta e identifica empresa, filtros e geração. |
| RF14 | Interface web | Fluxos completos, responsivos e acessíveis, validados contra referências disponíveis. |
| RF15 | Autenticação, autorização e auditoria | Tentativas entre empresas negadas; eventos sensíveis rastreáveis. |

Requisitos não funcionais obrigatórios: `BigDecimal` de ponta a ponta; operações financeiras transacionais; isolamento por empresa; integridade referencial; testes unitários e de integração; migrações Flyway; nenhum segredo em código ou logs. Propostas: BRL inicialmente, valores persistidos em `numeric(19,2)`, rejeição de casas excedentes em vez de arredondamento silencioso; cálculos intermediários com maior precisão e arredondamento explicitamente aprovado. APIs devem serializar valores decimais como strings para evitar perda no JavaScript.

Definir antes de testes de carga: volume de empresas, usuários simultâneos, lançamentos/ano, tamanho máximo de arquivos, retenção, metas de latência, disponibilidade, RPO e RTO. Não há SLA aprovado. Proposta de benchmark: relatórios anuais com massa representativa, registrando tempo, memória e planos de execução; fixar limites de aceite antes da homologação.

## 4. Arquitetura do backend e opções para frontend

Proposta: monólito modular Spring Boot, REST em `/api/v1`, PostgreSQL único e transações locais. Evita coordenação distribuída para o núcleo contábil e permite separar módulos futuramente conforme evidência de necessidade. Não introduzir microserviços, mensageria ou cache de saldos como premissa inicial.

Preservar `org.example` como raiz, conforme AGENTS.md. Propor pacotes por domínio: `empresa`, `identidade`, `conta`, `periodo`, `lancamento`, `abertura`, `venda`, `importacao`, `relatorio`, `exportacao`, `auditoria`. Dentro de cada módulo: controllers/DTOs, serviços e persistência; controllers não calculam saldos nem acessam diretamente regras de escrituração. Compartilhar apenas tipos técnicos pequenos, evitando um módulo genérico com regras de todos os domínios.

Serviços transacionais aplicam invariantes; DTOs impedem atualização indevida de empresa, autoria e estado. JPA atende gravações; consultas agregadas podem usar projeções e SQL revisado. Relatórios usam snapshot consistente de leitura, com instante de geração e versão da política; exportações derivam do mesmo resultado. Jobs de importação/exportação podem ser persistidos no próprio banco; execução assíncrona e armazenamento de arquivos dependem do volume aprovado.

| Opção proposta | Benefícios | Custos/decisão |
| --- | --- | --- |
| Spring MVC + Thymeleaf | Um deploy, sessão e formulários simples | Menor flexibilidade para grades e prévias complexas; dependência não existente. |
| React + TypeScript + Vite | Boa composição de grades, filtros e prévias | Novo projeto frontend, build e dependências; requer aprovação. |
| Vue + TypeScript + Vite | Alternativa para interface interativa | Mesmas decisões de implantação e manutenção de uma SPA. |

Recomendação para aprovação: SPA TypeScript servida na mesma origem do backend e autenticação por sessão, reduzindo complexidade de CORS e armazenamento de tokens. A escolha não está definida. Prototipar grade contábil e prévia antes de decidir. Navegação proposta: empresa ativa sempre visível; Cadastros, Movimentos, Importações, Livros, Demonstrações, Administração. Implementar telas incrementais desde as fases iniciais; a fase 8 conclui equivalência e acabamento.

## 5. Modelo de dados e relacionamentos

Proposta de identificadores UUID, datas contábeis como `LocalDate`, instantes de auditoria em UTC e versão otimista nos registros editáveis. Todas as entidades empresariais contêm `empresa_id NOT NULL`; as globais são explicitadas abaixo.

| Entidade proposta | Campos/relações principais e restrições |
| --- | --- |
| Usuario (global) | Identidade, estado e vínculo ao mecanismo de autenticação. |
| Empresa | Identificação, nome, documento validado, exercício, moeda e estado; política para filiais e unicidade a aprovar. |
| UsuarioEmpresa | N:N usuário–empresa; papel e estado; único por par. |
| PeriodoContabil | Empresa 1:N períodos; início/fim, aberto/fechado, responsável; impedir sobreposição na granularidade aprovada. |
| Conta | Empresa 1:N contas; código único por empresa, nome, pai da mesma empresa, natureza D/C, tipo, analítica/sintética, vigência e estado. |
| ModeloPlano/ModeloConta (globais) | Modelos versionados copiados para cada empresa; alterações posteriores não propagam automaticamente. |
| MapeamentoDemonstracao | Empresa/conta, linha de BP/DRE/DFC, vigência e versão; impedir dupla classificação incompatível. |
| Lancamento | Empresa, número sequencial, data, histórico, origem, estado, autor, contabilização, chave idempotente e referência de estorno. |
| Partida | N:1 lançamento; conta, lado D/C e valor positivo; pelo menos duas partidas por lançamento contabilizado. |
| Abertura/ItemAbertura | Empresa, data de corte, conta, saldo devedor/credor, estado e lançamento gerado; unicidade por abertura inicial aprovada. |
| LoteVenda/ItemVenda | Período, fonte, referência externa, datas, bruto, descontos, devoluções, tributos, líquido e agrupamento; vínculo com contabilização. |
| LoteImportacao/LinhaImportacao/ErroImportacao | Empresa, tipo/layout, hash, arquivo, versão, estado, linhas normalizadas, erros e resultado. |
| AlocacaoFluxoCaixa | Relação com partidas/documentos, categoria DFC e valor; permite dividir movimentos compostos sem adivinhar a contrapartida. |
| EventoAuditoria | Empresa quando aplicável, ator, operação, alvo, instante, correlação e mudança pertinente, sem segredo. |
| Exportacao | Empresa, solicitante, parâmetros, versão/snapshot, estado, arquivo privado e expiração. |

Empresa possui contas, períodos, lançamentos, vendas e lotes; lançamento possui partidas e pode referenciar outro lançamento por estorno. Venda/importação deve manter vínculo até os lançamentos gerados. Documentos de suporte podem ter metadados e armazenamento privado externo ao banco, com retenção a aprovar.

Chaves estrangeiras compostas incluindo `empresa_id` devem impedir referências cruzadas mesmo com falha de serviço. Índices propostos: lançamentos `(empresa_id,data,numero)`, partidas `(empresa_id,conta_id,lancamento_id)`, contas `(empresa_id,codigo)` e referências externas por empresa/fonte. Não reutilizar códigos/naturezas de contas já movimentadas de modo que reinterprete o passado. Inativação substitui exclusão de cadastros utilizados.

## 6. Módulos e dependências

Ordem lógica: identidade + empresa → contas + períodos → motor contábil → abertura e vendas → livros → demonstrações → exportações. Importação depende de autorização, validação de layout e do serviço do domínio de destino; nunca grava partidas diretamente. Relatórios dependem apenas da escrituração contabilizada e dos mapeamentos vigentes, não de totais paralelos de vendas.

Auditoria acompanha cada módulo desde a fundação. Autorização não depende do frontend. Abertura exige motor mínimo antecipado: embora sua interface seja fase 3, não terá um segundo mecanismo de contabilização descartável. Fase 4 amplia e endurece esse motor compartilhado.

## 7. Endpoints REST previstos

Todos abaixo são propostas; somente `/api/status` existe. Prefixo `/api/v1`; recursos empresariais usam `/empresas/{empresaId}` (abreviado `E`). IDs da URL também são autorizados no servidor.

| Método e rota | Operação |
| --- | --- |
| POST `/sessao`; GET `/sessao`; DELETE `/sessao` | Entrar, consultar contexto e sair, se sessão local aprovada. |
| GET/POST `/empresas`; GET/PATCH `E` | Listar permitidas, cadastrar, consultar/editar empresa. |
| PUT `/preferencias/empresa-ativa` | Preferência individual; não concede autorização. |
| GET/POST/PATCH `E/usuarios` ou `E/usuarios/{id}` | Administrar vínculos e papéis. |
| GET/POST `E/contas`; GET/PATCH `E/contas/{id}` | Consultar e manter plano. |
| POST `E/planos-padrao/{modeloId}/aplicacoes` | Aplicar cópia versionada, com controle de repetição. |
| GET `E/periodos`; POST `E/periodos/{id}/fechamento` | Consultar e fechar período; reabertura exige permissão e motivo. |
| POST/GET `E/aberturas`; PUT `E/aberturas/{id}/itens` | Preparar e consultar saldos iniciais. |
| POST `E/aberturas/{id}/confirmacao` | Gerar abertura pelo motor transacional. |
| GET/POST `E/lancamentos`; GET/PATCH `E/lancamentos/{id}` | Listar/criar e editar somente rascunho. |
| POST `E/lancamentos/{id}/contabilizacao` | Validar e contabilizar atomicamente. |
| POST `E/lancamentos/{id}/estornos` | Criar inverso vinculado, com motivo e data válida. |
| GET/POST `E/vendas-consolidadas` | Consultar e preparar consolidação. |
| POST `E/vendas-consolidadas/{id}/contabilizacao` | Contabilizar fontes validadas uma única vez. |
| POST `E/importacoes`; GET `E/importacoes/{id}` | Upload de contas/vendas e estado do processamento. |
| GET `E/importacoes/{id}/previa`; GET `E/importacoes/{id}/erros` | Prévia paginada e erros exportáveis. |
| POST `E/importacoes/{id}/confirmacao` | Confirmar versão validada de forma idempotente. |
| GET `E/relatorios/{diario,razao,balancete,balanco,dre,dfc}` | Uma rota por relatório; filtros explícitos de datas e contas. |
| POST `E/exportacoes`; GET `E/exportacoes/{id}`; GET `E/exportacoes/{id}/arquivo` | Solicitar, acompanhar e baixar PDF/XLSX privado, incluindo plano de contas. |
| GET `E/auditoria` | Consulta restrita por alvo, ator e período. |

Propor paginação e ordenação estável, limite máximo de página, datas ISO, contrato OpenAPI, erros padronizados com código/campo/linha/correlação. Respostas: 400 para formato inválido, 401 sem autenticação, 403 sem permissão (ou 404 para ocultar existência, política a aprovar), 409 para versão/duplicidade/estado conflitante e 422 para regra de negócio. POST financeiros aceitam chave idempotente vinculada a empresa, operação e hash do payload; repetição devolve resultado anterior, payload diferente gera conflito.

## 8. Cadastro e seleção de empresas

1. Autenticar; apresentar somente vínculos ativos. Sem empresa, oferecer cadastro apenas com permissão correspondente.
2. Validar identificação, documento e política de duplicidade; criar empresa e vínculo do responsável na mesma transação, sem conceder privilégios globais.
3. Configurar moeda, exercício, períodos e perfil contábil aprovado; oferecer plano manual, padrão ou importado.
4. Selecionar empresa ativa como preferência de interface. Cada chamada mantém empresa explícita e valida vínculo e permissão novamente.
5. Na troca, limpar grades, filtros, prévias e caches empresariais; descartar ou confirmar rascunhos locais e cancelar respostas pendentes da empresa anterior. Abas distintas devem poder trabalhar com empresas distintas sem vazamento por sessão global.
6. Revogação de vínculo deve impedir imediatamente novas leituras, confirmações e downloads, mesmo de jobs iniciados antes.

## 9. Fluxo de lançamentos contábeis

Estados propostos: RASCUNHO → CONTABILIZADO. Estorno é novo lançamento contabilizado; o original permanece nos livros e recebe relação de estorno, nunca é filtrado dos totais históricos. Rascunhos não entram nos relatórios oficiais.

Na contabilização: autorizar empresa/papel, bloquear ou validar estado e período concorrente, verificar contas analíticas ativas/vigentes da mesma empresa, data em período aberto, histórico e suporte, partidas positivas e soma exata dos débitos igual à dos créditos. Valores são construídos de strings decimais. Persistir número sequencial por empresa/exercício, partidas, origem, idempotência e auditoria na mesma transação. Não usar `max(numero)+1` sem serialização. Definir política de lacunas e ordenação com contador.

Concorrência entre fechamento e contabilização exige bloqueio compartilhado pelo protocolo de ambos, impedindo lançamento após fechamento. Proposta adicional: constraint trigger diferida validando equilíbrio na transição e restrição de mutação das partidas contabilizadas; uma CHECK de linha não valida soma entre linhas. A escolha deve ser testada no PostgreSQL.

Estorno exige motivo, referência única ao original e permissão específica; inverte lados com mesmos valores. Proposta inicial: estorno integral único; correção gera novo lançamento separado. Data retroativa, período fechado, reabertura e estorno de estorno exigem política aprovada. Original e inverso são somados conforme suas datas, preservando posições históricas. Nenhuma exclusão física de lançamentos contabilizados.

## 10. Vendas consolidadas e importação Excel/CSV

Consolidação manual: escolher empresa, intervalo inclusivo, fontes, contas e regras de tributos/descontos/devoluções; apresentar memória de cálculo, validar e confirmar. Reconhecimento por competência e recebimento são eventos distintos: venda a prazo não é caixa. Taxas de cartão, impostos e custo da mercadoria não devem ser inferidos sem dados e política contábil. Sobreposição de períodos sozinha não prova duplicidade; controlar identidade das operações-fontes já contabilizadas.

Importação proposta:

1. Baixar template versionado; mapear colunas de referência externa, data, bruto, descontos, devoluções, tributos, líquido e modalidade/contas quando aplicável. Contrato final depende de amostras aprovadas; não inventar campos ausentes.
2. Upload autenticado, limite de tamanho/linhas/tempo, detecção de formato e armazenamento privado temporário. Propor XLSX inicialmente, CSV com encoding e delimitador explícitos; XLS legado requer decisão.
3. Parse sem executar fórmulas/macros; controlar arquivos compactados maliciosos, datas seriais Excel, separadores decimais, campos obrigatórios, contas e empresa. Linhas com fórmulas monetárias devem ser rejeitadas ou tratadas por política explícita, nunca avaliadas cegamente.
4. Normalizar preservando fonte, linha e valor original; identificar duplicidades dentro do arquivo e contra registros existentes. Hash do arquivo evita reenvio idêntico, mas chave externa por empresa/fonte é a defesa principal; fingerprint de conteúdo apenas sinaliza casos sem referência confiável.
5. Prévia informa linhas válidas/inválidas/duplicadas, totais e lançamentos previstos. Erros possuem linha, coluna, código e orientação. Nenhuma prévia altera o livro.
6. Confirmar versão/hash da prévia, revalidando permissões, contas, período e duplicidade contra mudanças concorrentes. Proposta inicial: lote limitado e confirmação integral; qualquer erro impede contabilização de todo o lote. Cargas grandes exigirão desenho explícito de lotes parciais e reconciliação, antes de habilitar essa modalidade.
7. Gerar consolidações e lançamentos pelo mesmo motor, preservar vínculo de cada linha ao resultado e auditar confirmação. Repetir confirmação após timeout retorna o mesmo resultado. Estados: RECEBIDO, VALIDANDO, COM_ERROS ou PRONTO, CONFIRMADO/CANCELADO/FALHOU.

Importação do plano usa o mesmo fluxo técnico, validando códigos únicos, hierarquia sem ciclos, pais existentes e natureza/classificação. Nunca sobrescrever contas movimentadas silenciosamente. Exportação sanitiza células contra injeção de fórmulas em Excel/CSV.

## 11. Regras de cálculo dos relatórios contábeis

Base única: partidas de lançamentos contabilizados da empresa, incluindo originais e estornos. Datas de competência determinam inclusão; autoria e timestamp não substituem a data contábil. Agregar com precisão integral e arredondar apenas conforme política aprovada. Conta sintética agrega descendentes sem somar novamente valores já agregados.

Convenção interna proposta: saldo algébrico = débitos − créditos. Saldo devedor = `max(saldo,0)` e credor = `max(-saldo,0)`. Exibição por natureza não altera os valores armazenados. Saldo inicial em um intervalo inclui toda movimentação anterior ao início e a abertura aplicável, sem contar abertura duas vezes.

| Relatório | Cálculo e reconciliação |
| --- | --- |
| Diário | Ordenar por data, número e ordem da partida; apresentar histórico, origem e totais D/C iguais; filtros não mudam o lançamento original. |
| Razão | Por conta: saldo anterior + débitos do período − créditos do período = saldo final; mostrar acumulado por partida e acesso ao Diário. |
| Balancete | Saldo inicial D/C, movimento D/C e saldo final D/C por conta; total de movimentos D=C e saldo devedor total=credor total para escopo completo e equilibrado. |
| BP | Posição acumulada na data, classificada por mapeamento vigente em ativo, passivo e PL; testar Ativo = Passivo + PL, incluindo resultado ainda não encerrado sem duplicá-lo. |
| DRE | Receitas, deduções, custos, despesas e demais resultados por competência no intervalo; sinal de apresentação deriva do mapeamento e contas retificadoras. Resultado deve reconciliar com contas de resultado. |
| DFC | Proposta: método direto inicialmente, com fluxos operacionais, investimento e financiamento. Saldo inicial de caixa + fluxos líquidos + efeitos de reconciliação aplicáveis = saldo final de caixa/equivalentes. Método indireto é alternativa a aprovar. |

DFC exige contas de caixa/equivalentes identificadas e alocações de fluxos para lançamentos compostos. Transferências entre caixa e equivalentes não são recebimento/pagamento externo; operações sem caixa não entram nos fluxos. Diferenças cambiais e divulgações adicionais dependem do escopo monetário aprovado. Não derivar DFC apenas de vendas nem classificar automaticamente toda contrapartida bancária como operacional.

Abertura representa a posição anterior à operação no sistema: aprovar data de corte, origem dos saldos e inclusão de resultados em implantação no meio do exercício. Não criar conta de ajuste para esconder desequilíbrio. Encerramento de resultado deve ser identificado tecnicamente; DRE não pode zerar por incluir indiscriminadamente lançamentos de encerramento, enquanto BP incorpora a transferência ao PL uma única vez. Definir reabertura e comparativos antes de homologar demonstrações.

Validação profissional obrigatória: perfil normativo por porte/natureza da entidade; estrutura e vigência das demonstrações; reconhecimento de receita; tributos; abertura; encerramento; contas retificadoras; classificação de juros/dividendos e equivalentes na DFC. Não prometer conformidade fiscal, SPED/ECD, assinatura ou livros autenticados: esses itens não foram solicitados como entrega e dependem de escopo próprio.

Referências oficiais consultadas em 04/11/2026: o [catálogo do CFC](https://portalrestore.cfc.org.br/tecnica/normas-brasileiras-de-contabilidade/normas-especificas/) identifica a ITG 2000 (R1), Escrituração Contábil. O [CPC 03 (R2)](https://www.cpc.org.br/CPC/Documentos-emitidos/Pronunciamentos/Pronunciamento?Id=34) trata da DFC. Esses documentos orientam a validação, sem substituir a determinação do regime normativo aplicável pelo contador. Consultar também o [catálogo oficial de pronunciamentos](https://www.cpc.org.br/CPC/Documentos-Emitidos/Pronunciamentos/) para confirmar versão vigente do CPC 26 e demais normas pertinentes antes de fechar o desenho de apresentação. Não assumir aplicação universal de um único conjunto normativo.

## 12. Segurança e trilha de auditoria

Propor Spring Security desde a fase 1, com papéis por empresa: administrador empresarial, contador, operador e consulta; matriz de permissões deve ser aprovada. Administração global não concede acesso contábil implicitamente. Separação entre lançamento e aprovação pode ser habilitada conforme governança definida.

Sessão na mesma origem: cookies HttpOnly/Secure/SameSite, CSRF nos comandos, expiração e logout efetivo. Se identidade externa/OIDC for escolhida, rever endpoints de sessão e provisionamento. Se senha local for escolhida, usar hash adaptativo suportado pelo framework, recuperação segura e proteção contra tentativas excessivas. TLS, segredos externos e menor privilégio no banco; papel de migração separado do papel de execução. Frontend nunca é barreira de autorização.

Toda consulta, join, download, job e chave de cache deve incluir empresa e autorização. RLS PostgreSQL é defesa adicional proposta, exigindo contexto transacional seguro em pool de conexões e testes para jobs; não substitui escopo no serviço e FKs compostas. Bloquear mass assignment e exposição de entidades JPA. SQL parametrizado, validação de uploads e proteção de exportações.

Auditar criação/edição de cadastros, contabilização, estorno, importação, fechamento/reabertura, mudança de permissão e geração de relatórios sensíveis. Evento contém ator, empresa, instante, entidade, ação, motivo, correlação e valores pertinentes. Eventos financeiros de sucesso são atômicos com o fato; tentativas negadas e falhas usam registro de segurança separado, sem registrar sucesso de transação revertida. Restringir UPDATE/DELETE da auditoria; proteção contra adulteração por administrador e retenção imutável externa são decisões de operação.

Planejar minimização, retenção e acesso a dados pessoais com responsável pela privacidade. Não registrar senhas, tokens, arquivos completos ou dados sensíveis em logs; revisar `show-sql` antes de produção. Backups criptografados e restauração ensaiada são requisitos de implantação, com metas a aprovar.

## 13. Estratégia de migração do banco existente

Não há evidência sobre tabelas ou dados atuais. Nenhuma migração será executada nesta tarefa. Plano condicionado à aprovação futura:

1. Inventariar ambiente, versão, esquema, extensões, tabelas, constraints e dados com acesso somente leitura autorizado; identificar divergências geradas por Hibernate.
2. Produzir backup e ensaiar restauração em cópia isolada; comparar esquema real com entidades futuras e registrar checksum do diagnóstico.
3. Adicionar Flyway compatível com a plataforma, incluindo suporte PostgreSQL necessário. Criar migrações SQL versionadas em `src/main/resources/db/migration`, revisadas e testadas; não alterar migração já aplicada.
4. Banco vazio: executar cadeia inicial desde a primeira versão. Banco não vazio: criar baseline explícita apenas após reconciliação; não habilitar baseline automático que esconda estruturas desconhecidas. Garantir que criação do zero e evolução da cópia resultem no mesmo esquema esperado.
5. Antes de qualquer inicialização no banco real, substituir `ddl-auto=update` por `validate` no fluxo aprovado; não deixar Hibernate e Flyway competirem na gestão do esquema.
6. Dados sem empresa precisam de mapeamento validado, nunca atribuição arbitrária. Introduzir campos, preencher/verificar e só depois exigir NOT NULL/FKs. Reconciliar contagens, totais D/C e saldos antes/depois.
7. Aplicar em homologação, executar testes e obter aprovação do responsável por dados. Planejar janela, locks, compatibilidade de versões e recuperação. Preferir correção progressiva; restauração envolve possível perda de escritas posteriores e exige procedimento explícito.

Não editar Compose, remover volume ou iniciar aplicação para descobrir o banco. Usuários de aplicação não devem possuir privilégios DDL em produção.

## 14. Estratégia de testes

| Camada | Casos e evidências |
| --- | --- |
| Unidade | Equilíbrio, escala, arredondamento, hierarquia, período, estorno, deduplicação e fórmulas de relatórios; sem banco. |
| Integração PostgreSQL 17 | Propor Testcontainers; migrações, FKs entre empresas, precisão NUMERIC, consultas, constraints diferidas e rollback real. Não substituir por H2 como prova de compatibilidade. |
| Concorrência | Duas confirmações, mesma chave externa, dois estornos e fechamento concorrente; provar um único efeito e ausência de dados parciais. |
| API e segurança | Matriz de papéis, usuário sem vínculo, IDs de outra empresa, CSRF, revogação e downloads/jobs; testar respostas e ausência de vazamentos. |
| Importação | Arquivos pequenos reais de teste: UTF-8, separadores, datas Excel, valores inválidos, fórmulas, duplicidades e limites; prévia sem escrituração. |
| Relatórios | Massa contábil pequena aprovada pelo contador: abertura, venda a prazo, recebimento, despesa, transferência de caixa, estorno e encerramento. Valores esperados calculados independentemente. |
| Exportação/UI | PDF/XLSX coincide com consulta; datas e centavos preservados; navegação por teclado, troca de empresa e fluxo de erro/retentativa. |
| Operação | Migração de cópia, restauração, carga acordada e recuperação de jobs interrompidos. |

Fixtures devem conter ao menos duas empresas com dados similares e usuários com permissões diferentes. Verificar Razão → Balancete → BP/DRE e reconciliação DFC → caixa; testar relatório histórico após estorno futuro. Relatórios paginados não podem calcular totais apenas da página. `mvn test` para unidade e proposta de integração no ciclo `mvn verify`; pipeline deve executar ambos antes da imagem, pois o Dockerfile atual pula testes. Não foram executados testes nesta tarefa, por não haver alteração executável.

## 15. Etapas de desenvolvimento e dependências

| Fase | Entrega proposta | Dependências e porta de saída |
| --- | --- | --- |
| 1 — Fundação e empresas | Decisões mínimas, Flyway, identidade, autorização empresarial, auditoria base e cadastro/seleção | Banco diagnosticado e política aprovada; duas empresas isoladas em testes. |
| 2 — Plano de contas | Manual, modelo versionado, importação/exportação do plano | F1; hierarquia e contas movimentáveis validadas. |
| 3 — Balancete inicial | Rascunho de saldos e confirmação pelo motor mínimo | F2 + núcleo transacional antecipado; abertura equilibrada e única. |
| 4 — Motor e estornos | Lançamentos compostos, imutabilidade, períodos e concorrência | F3; invariantes e estorno comprovados por testes. |
| 5 — Vendas/importações | Consolidação, prévia, deduplicação e erros Excel/CSV | F4; confirmação idempotente e reconciliação das fontes. |
| 6 — Livros | Diário, Razão e Balancete | F4 e fixtures aprovadas; totais reconciliados, inclusive dados F5. |
| 7 — Demonstrações | BP, DRE, DFC e mapeamentos versionados | F6 + decisões contábeis; parecer de homologação do contador. |
| 8 — Exportação/interface | PDF/Excel gerais, navegação completa e equivalência de fluxos | F6/F7 + referências disponíveis; igualdade entre tela e exportação. |
| 9 — Preparação operacional | Revisão de segurança/auditoria, testes completos, carga, backups e implantação | Todas; restauração ensaiada e aceite funcional/operacional. |

Segurança, auditoria, testes e telas mínimas acompanham todas as fases; F9 não é o início dessas atividades. Exportação Excel do plano acontece em F2; F8 generaliza relatórios. Não estimar calendário antes de definir equipe, frontend, layouts, massa e regras pendentes. Cada fase deve produzir demonstração revisável e evidências de aceite antes da seguinte dependência crítica.

## 16. Backlog detalhado

Cada linha é uma tarefa pequena; IDs de dependência referem-se a tarefas deste quadro. Todas são futuras e precisam de autorização de execução; este plano não as iniciou.

| ID/Fase | Tarefa | Depende de | Critério de aceite verificável |
| --- | --- | --- | --- |
| T01/1 | Inventariar telas e operações da referência | Acesso/telas | Matriz de telas, campos e ações revisada pelo usuário, com lacunas identificadas. |
| T02/1 | Registrar decisões de frontend, identidade e escopo monetário | Aprovação | Decisões explícitas registradas, sem converter propostas em pressupostos. |
| T03/1 | Diagnosticar esquema e ensaiar backup em cópia | Acesso autorizado | Inventário sem segredos e restauração comprovada sem tocar no original. |
| T04/1 | Introduzir Flyway e validação de esquema | T03 | Banco vazio e cópia existente chegam ao esquema esperado; sem Hibernate update. |
| T05/1 | Implementar autenticação mínima | T02 | Entrar/sair, expirar e negar acesso anônimo em teste. |
| T06/1 | Criar empresa e vínculos de usuário | T04,T05 | Criação atômica; lista mostra somente empresas permitidas. |
| T07/1 | Centralizar autorização por empresa | T06 | IDs de outra empresa negados em leitura e escrita automatizadas. |
| T08/1 | Registrar auditoria base | T07 | Alteração confirmada gera evento; rollback não gera falso sucesso. |
| T09/1 | Criar seletor de empresa | T02,T07 | Troca não exibe resposta/cache da empresa anterior, inclusive em duas abas. |
| T10/2 | Persistir hierarquia de contas | T04,T07 | Código único por empresa, pai da mesma empresa e ciclo rejeitado. |
| T11/2 | Criar cadastro e inativação de contas | T10 | Conta sintética não movimentável; conta utilizada não é excluída. |
| T12/2 | Aplicar plano padrão versionado | T11 | Cópia pertence à empresa e repetição não duplica contas. |
| T13/2 | Definir template Excel do plano | T11 | Arquivo de exemplo revisado, com regras e versão de layout. |
| T14/2 | Pré-visualizar e confirmar importação de contas | T13 | Ciclos/duplicidades produzem erros por linha e zero gravação parcial. |
| T15/2 | Exportar plano Excel | T13,T14 | Exportar e reimportar preserva hierarquia e códigos. |
| T16/3 | Modelar períodos e protocolo de fechamento | T04,T07 | Datas inválidas/sobreposições rejeitadas conforme política aprovada. |
| T17/3 | Criar motor mínimo de contabilização | T11,T16,T08 | Partidas válidas persistem juntas; desequilíbrio causa rollback completo. |
| T18/3 | Preparar balancete inicial editável | T11,T16 | Totais D/C e diferenças exibidos antes da confirmação. |
| T19/3 | Confirmar abertura | T17,T18 | Gera lançamento rastreável uma vez e impede abertura desbalanceada. |
| T20/4 | Completar rascunhos e lançamentos compostos | T17 | Editar apenas rascunho; contabilizado permanece imutável. |
| T21/4 | Implementar sequência e idempotência | T20 | Requisições concorrentes não repetem número nem efeito financeiro. |
| T22/4 | Implementar estorno integral vinculado | T20,T21 | Original + inverso anulam valores nas mesmas contas; duplo estorno negado. |
| T23/4 | Fechar/reabrir com controle concorrente | T16,T20 | Corrida fechamento/lançamento não permite escrita em período fechado. |
| T24/5 | Aprovar layout de vendas e regras de contabilização | T01,contador | Amostras de venda à vista/a prazo, descontos e devoluções têm partidas esperadas. |
| T25/5 | Consolidar vendas manualmente | T24,T20 | Memória de cálculo corresponde às fontes e aos lançamentos gerados. |
| T26/5 | Criar parser CSV | T24 | Encoding, delimitador, datas e valores inválidos cobertos por fixtures. |
| T27/5 | Criar parser XLSX com limites | T24 | Datas seriais e limites tratados; fórmulas não são executadas. |
| T28/5 | Persistir prévia e erros de importação | T26,T27 | Erros apontam linha/campo; nenhuma partida existe antes de confirmar. |
| T29/5 | Deduplicar e confirmar lote | T28,T21,T25 | Reenvio e duas confirmações geram um efeito; falha reverte lote integral. |
| T30/5 | Vincular fontes ao estorno/correção | T29,T22 | Da linha chega-se ao lançamento, inverso e nova contabilização autorizada. |
| T31/6 | Consultar Diário | T20,T22 | Ordem estável, filtros e totais conferem com massa conhecida. |
| T32/6 | Consultar Razão | T31 | Saldo anterior + movimento = final para cada conta e período. |
| T33/6 | Consultar Balancete | T32 | D=C nos totais globais e sintéticas não duplicam analíticas. |
| T34/7 | Configurar mapeamentos versionados | T33,contador | Contas sem classificação são sinalizadas; vigência preserva histórico. |
| T35/7 | Gerar BP | T34 | Ativo = Passivo + PL, com resultado sem dupla contagem. |
| T36/7 | Gerar DRE e política de encerramento | T34 | Resultado coincide com fixture inclusive após encerramento técnico. |
| T37/7 | Classificar fluxos e gerar DFC | T34,método aprovado | Transferência interna excluída e variação de caixa reconciliada. |
| T38/8 | Exportar PDF de relatório | T31–T37 | Conteúdo coincide com consulta, sem truncar partidas ou totais. |
| T39/8 | Exportar Excel de relatório | T31–T37 | Centavos preservados, células seguras e filtros identificados. |
| T40/8 | Completar navegação e acessibilidade | T01,T09,T38,T39 | Matriz funcional coberta, teclado utilizável e erros recuperáveis. |
| T41/9 | Executar revisão de segurança e isolamento | T07,T29,T40 | Matriz de endpoints/jobs/downloads sem acesso cruzado. |
| T42/9 | Automatizar verificação e build de entrega | Fases anteriores | Unidade e integração bloqueiam empacotamento quando falham. |
| T43/9 | Medir carga e recuperação | Metas aprovadas,T42 | Limites acordados atendidos; backup restaurado e jobs retomados sem duplicar. |
| T44/9 | Homologar e preparar implantação | T35–T43 | Contador e responsável técnico aprovam evidências e procedimento de recuperação. |

## 17. Riscos técnicos, decisões pendentes e aprovações

| Risco | Mitigação planejada | Responsável pela decisão |
| --- | --- | --- |
| Banco existente desconhecido e atualização automática | Diagnóstico, cópia restaurada, baseline revisada e `validate` antes do domínio | Responsável técnico/dados |
| Vazamento entre empresas | Autorização desde F1, FKs compostas, testes adversariais e avaliação de RLS | Técnico/segurança |
| Duplicidade financeira após retentativa | Chaves únicas, idempotência, vínculo com fontes e testes concorrentes | Técnico/produto |
| Saldos aparentemente corretos mas classificação errada | Mapeamentos versionados e fixtures aprovadas por contador | Contador |
| DFC sem dados suficientes | Coletar alocações e definir método antes de implementar relatório | Contador/produto |
| Arquivos hostis ou grandes | Limites, parsing sem execução, armazenamento privado e política de lote | Técnico/produto |
| Referência inacessível e telas ausentes | Inventário posterior e validação por fluxo, sem alegar equivalência já verificada | Produto |
| Escopo fiscal presumido | Delimitar escrituração gerencial/formal, obrigações acessórias e assinatura | Contador/produto |
| Alteração retroativa de relatórios | Imutabilidade, fechamento, mapeamentos vigentes e snapshots identificáveis | Contador/técnico |
| Perda de dados/indisponibilidade | Backups, restauração ensaiada, metas RPO/RTO e plano operacional | Operação |

Perguntas para aprovação, antes das tarefas dependentes:

1. Quais portes, regimes e naturezas de empresas serão atendidos? Quem homologa as regras e o conjunto normativo vigente? Haverá filiais no mesmo contexto ou entidades separadas?
2. Aprova-se monólito modular com banco compartilhado e `empresa_id`? RLS será obrigatória na primeira entrega ou defesa posterior?
3. Qual frontend será utilizado? Aprova-se SPA TypeScript na mesma origem? Onde obter telas e acesso funcional à referência?
4. Identidade local ou provedor externo? Quais papéis podem cadastrar empresas, contabilizar, estornar, fechar e reabrir períodos? É necessária dupla aprovação?
5. BRL e escala de duas casas atendem ao primeiro escopo? Qual arredondamento será homologado? Há multimoeda ou exercícios diferentes do ano civil?
6. Qual data de corte da abertura, política de implantação no meio do ano, encerramento, estorno e reabertura? Como tratar lacunas de numeração?
7. Quais layouts reais de vendas e plano serão aceitos, inclusive XLS legado? Existe referência externa confiável? Confirmação integral do lote atende ao volume esperado?
8. DFC direta é adequada? Quais classes de caixa/equivalentes e critérios para juros, tributos e dividendos serão adotados? Quais comparativos e notas são necessários?
9. Qual ambiente de implantação, armazenamento privado, retenção e exigência de trilha imutável? Quais volumes, limites, latência, disponibilidade, RPO e RTO?
10. PDF/Excel são relatórios de apoio ou devem atender formalidades adicionais? SPED/ECD e assinatura permanecem fora do escopo inicial?

Primeiro pacote recomendado para autorização: T01–T03 (referências, decisões e diagnóstico somente leitura/cópia autorizada). Após esse aceite, autorizar T04–T09 para fundação, empresas, identidade e isolamento. Aprovar contratos contábeis de abertura e venda antes das fases 3 e 5, e método/mapeamentos das demonstrações antes da fase 7. Não solicitar aprovação de execução de migração sem primeiro apresentar scripts, diagnóstico e plano de recuperação revisáveis.

### Verificação desta entrega documental

Criado somente `docs/PLANO_DESENVOLVIMENTO.md`. AGENTS.md preservado. Nenhum código, Compose ou banco alterado; nenhum serviço iniciado, teste executado, migração aplicada ou commit criado. As recomendações não equivalem a decisões técnicas aprovadas. Teste
