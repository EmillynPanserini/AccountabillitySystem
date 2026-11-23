# Repository Guidelines

## Base técnica e organização
Use Java 21, Spring Boot 3.5.7 e Maven, conforme `pom.xml`. O projeto utiliza Spring Web, Spring Data JPA e PostgreSQL; `compose.yaml` define PostgreSQL 17 e a aplicação. O README atual contém apenas o nome do projeto; não presuma funcionalidades implementadas.

Mantenha código em `src/main/java/org/example`, configurações em `src/main/resources` e testes em `src/test/java`. Organize novos pacotes por domínio sob `org.example`, com separação entre controllers, serviços, persistência e DTOs. Preserve o escaneamento de componentes iniciado por `Main`. Controllers devem delegar regras de negócio aos serviços; delimite transações nos serviços.

## Execução e testes
Use `mvn test` para testes, `mvn verify` para validação completa e `mvn spring-boot:run` para execução local com ambiente configurado. `docker compose up --build` inicia aplicação e banco; execute apenas quando necessário e autorizado pelo escopo. Não remova volumes nem altere dados ou esquema sem autorização. A configuração atual usa `ddl-auto=update`: iniciar a aplicação pode modificar o esquema; não aponte para bancos compartilhados ou de produção.

Use JUnit e os recursos de `spring-boot-starter-test`. Adicione testes automatizados para regras contábeis, validações, isolamento por empresa e falhas transacionais. Testes de persistência devem usar PostgreSQL isolado, sem depender de dados reais. Cubra importações duplicadas, arredondamentos e rejeição de lançamentos desbalanceados.

## Integridade contábil e segurança
Priorize partidas dobradas: todo lançamento deve equilibrar débitos e créditos e ser gravado atomicamente. Use `BigDecimal`, nunca `float` ou `double`, para valores monetários; defina escala e arredondamento explicitamente. Valide empresa, contas, datas e períodos; aplique isolamento por empresa em consultas, gravações, importações e relatórios, com autorização no servidor.

Preserve rastreabilidade de origem, autoria, data e alterações. Corrija lançamentos contabilizados por estorno rastreável. Importações Excel/CSV devem validar dados e prevenir duplicidade. Diário, Razão, Balancete, Balanço, DRE e DFC devem reconciliar com os lançamentos.

Nunca registre ou versione senhas, credenciais, conteúdo de `.env` ou dados empresariais sensíveis. Use variáveis de ambiente e evite informações sensíveis em logs.

## Commits e escopo
O histórico contém `Initial commit` e duas mensagens `Forçando build no pipeline`: mensagens curtas, sem convenção formal demonstrada. Use descrições curtas e fiéis à alteração; não imponha Conventional Commits. Mantenha mudanças focadas e informe verificações executadas. Não implemente funcionalidades nem altere banco em tarefas exclusivamente documentais.
