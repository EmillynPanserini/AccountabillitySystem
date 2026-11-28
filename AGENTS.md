# Regras do Sistema de Contabilidade

## Leitura e fontes de verdade

Leia este arquivo e consulte apenas os arquivos pertinentes à tarefa. Regras técnicas e de desenvolvimento ficam aqui; o [fluxo da equipe](.codex/FLUXO-DA-EQUIPE.md) descreve a passagem entre os papéis. [Requisitos](docs/requisitos-academicos.md) registram fontes e aceite; [decisões e cronograma](docs/decisoes-e-cronograma.md) registram escolhas, prioridades e correspondências do backlog; [andamento](docs/andamento.md) registra o estado operacional.

Planejamentos e retomadas anteriores são históricos preservados, não instruções operacionais concorrentes. Consulte-os pelo assunto referenciado, sem reler auditorias em toda tarefa. Não converta proposta antiga em decisão aprovada nem presença de dependência em funcionalidade implementada.

## Base técnica e arquitetura

- Manter Java 21, Spring Boot 3.5.7, Angular, TypeScript, PostgreSQL 17, Flyway e Maven Wrapper. Versões de dependências estão em [pom.xml](pom.xml), [package.json](frontend/package.json) e lockfiles.
- Usar monólito modular simples, organizado por funcionalidade. Java em `src/main/java/org/example`, recursos em `src/main/resources`, testes em `src/test/java` e frontend em `frontend/`. Preservar o escaneamento iniciado por `Main`.
- Dentro de cada funcionalidade, separar API/DTOs, serviços e persistência conforme a necessidade. Regras de negócio e autorização ficam no backend; controllers delegam aos serviços, que delimitam transações.
- O estado implementado inclui fundação web, Angular inicial e persistência sintética T05. O [README](README.md) contém comandos e operação. Não presumir identidade, empresas ou funcionalidades contábeis prontas.
- O perfil padrão desativa inicialização de persistência: `Main` exclui as autoconfigurações correspondentes; `application.properties` usa `ddl-auto=none`, Flyway desabilitado e inicialização SQL desabilitada. O perfil opt-in T05 usa atestação, Flyway e Hibernate `validate`. Isso não autoriza conexão ou migração em outro destino.

## Clean Code

Aplicar a [referência indicada pelo usuário](https://www.task.com.br/blog/clean-code) com estes critérios práticos:

1. Usar nomes claros e significativos para classes, métodos, variáveis e arquivos.
2. Manter convenções da linguagem: tipos Java/TypeScript em PascalCase, métodos e variáveis em camelCase; pacotes Java em minúsculas e arquivos Angular conforme o padrão adotado no frontend.
3. Escrever métodos pequenos e coesos, com responsabilidade clara.
4. Aplicar responsabilidade única sem fragmentar o código excessivamente.
5. Evitar duplicação de regras e lógica de negócio; compartilhar apenas o que tiver necessidade real.
6. Comentar decisões e razões importantes, sem repetir código evidente.
7. Evitar excesso de parâmetros; introduzir objetos apropriados apenas quando melhorarem o modelo e a leitura.
8. Organizar por funcionalidade, com responsabilidades e dependências claras.
9. Evitar classes gigantes, condições confusas, acoplamento desnecessário e código morto.
10. Testar comportamentos relevantes, incluindo falhas, limites e regressões.
11. Preferir soluções simples, claras e funcionais.
12. Não introduzir padrões, interfaces, frameworks ou abstrações sem necessidade comprovada.

Não impor limites artificiais de linhas ou parâmetros, refatorações gerais ou abstrações para cumprir uma lista. Limpeza deve permanecer no escopo autorizado e facilitar a leitura.

## Integridade contábil e segurança

- Todo lançamento deve equilibrar débitos e créditos e ser gravado atomicamente. Usar `BigDecimal`, nunca `float` ou `double`, para dinheiro; definir escala e arredondamento explicitamente.
- Validar empresa, contas, datas e períodos. Conferir autorização no servidor em consultas, gravações, importações e relatórios; impedir referências e acesso cruzados entre empresas.
- Preservar origem, autoria, data e alterações. Corrigir lançamentos contabilizados por estorno rastreável. Validar Excel/CSV, prevenir duplicidade e reconciliar Diário, Razão, Balancete, Balanço, DRE e DFC com a escrituração.
- Nunca ler, compartilhar, registrar ou versionar conteúdo de `.env`, senhas, tokens, credenciais ou dados empresariais sensíveis. Usar variáveis de ambiente e evidências sanitizadas.
- Preservar arquivos, trabalho local, históricos, migrações, código funcional, bancos e volumes. Não reverter alterações alheias nem excluir arquivos inteiros fora do escopo; apresentar caminhos, justificativas, referências e impactos antes de solicitar autorização de exclusão.
- Acesso a bancos, migrações, Docker/Compose/volumes, bootstrap operacional, commits, pushes e operações destrutivas exigem autorização específica. Aprovação de tarefa não autoriza essas operações. Não apagar volumes nem alterar dados reais sem autorização própria.
- Não iniciar aplicação ou testes contra banco real/compartilhado. Persistência exige PostgreSQL isolado, sem dados reais, destino comprovado e operação autorizada; sem prova de isolamento, registrar bloqueio e não executar. Worktree não isola banco ou credenciais. Não usar MCP para contornar restrições.

## Execução e evidências

Usar Maven Wrapper: na raiz, `.\mvnw.cmd -B -ntp test` para testes e `.\mvnw.cmd -B -ntp clean verify` para validação completa; em Linux, `bash ./mvnw` com os mesmos argumentos. No frontend, os scripts de teste/build estão no `package.json`; procedimentos ficam no README. Instalações e serviços devem respeitar o escopo autorizado.

JUnit e Spring Boot Test são a base Java. Cobrir invariantes contábeis, validações, isolamento empresarial, transações/rollback, importações duplicadas, arredondamentos e rejeição de lançamentos desbalanceados quando pertinentes. Executar a menor verificação suficiente para a mudança, ampliando diante de falhas ou riscos concretos. Edições documentais exigem verificações documentais, sem iniciar aplicação ou infraestrutura desnecessariamente.

Cada resultado informa comando, diretório, data, versão/hash pertinente, código de saída, totais, falhas, erros e ignorados quando aplicáveis. Diferenciar teste executado, escrito, proposto, bloqueado e evidência histórica. Compilação não comprova comportamento; não declarar sucesso com logs antigos. Correções invalidam as evidências afetadas e exigem nova verificação pertinente.

## Coordenação proporcional ao risco

A Coordenadora distribui trabalho aos quatro papéis existentes; não assume automaticamente especialidades. Somente ela delega; especialistas não criam subagentes. Se faltar especialista necessário, registrar impedimento. Cada passagem informa tarefa, autorização, aceite, arquivos exclusivos, resultado e próximo responsável. Nunca manter escritores simultâneos nos mesmos arquivos; encerrar escrita antes da verificação independente.

| Situação | Encaminhamento |
|---|---|
| Tarefa pequena com objetivo único, aprovação explícita, arquivos delimitados e aceite verificável; sem decisão pendente | Desenvolvedor diretamente, sem exigir novo plano do Planejador |
| Decisão relevante ausente, dependência indefinida ou mudança relevante de arquitetura | Planejador apresenta somente o plano/delta necessário para aprovação explícita |
| Baixo risco, sem os gatilhos abaixo | Desenvolvedor executa verificações pertinentes; não exigir Testador ou Auditor por rotina |
| Autenticação/sessão, autorização/isolamento empresarial, persistência/migrações, transações/concorrência, cálculos contábeis, importações ou contratos compartilhados | Testador independente após implementação estável |
| Fronteiras de autorização/isolamento, integridade financeira, operação irreversível ou marco sensível de identidade/empresa, governança ou liberação | Auditor focal após evidências pertinentes; justificar acionamento em uma frase |

Tamanho pequeno não reduz risco de segurança. Auditor não substitui Testador. Falhas voltam ao Desenvolvedor; achados sobre plano ou evidências voltam ao responsável. Mudança de escopo exige nova aprovação; aprovação já concedida permanece válida dentro do escopo. Silêncio e parecer técnico não autorizam implementação. A entrega só fica concluída após aprovação final explícita do usuário.

Relatórios curtos: tarefa, arquivos, testes/resultados, bloqueios e próximo passo. Não criar documentos de passagem nem repetir histórico de auditorias sem necessidade autorizada. Não iniciar trabalho somente ao carregar configuração. Commits, quando especificamente autorizados, usam descrições curtas e fiéis, sem impor convenção adicional.
