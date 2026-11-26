# Andamento do Sistema de Contabilidade

Atualização: 28/11/2026. Estado operacional; requisitos e decisões ficam nos documentos vinculados abaixo.

| Tarefa | Estado | Evidência / limite |
|---|---|---|
| Transição v1.1 | Aprovada e homologada pelo usuário | Aprovação expressa na conversa de início de T06 |
| T02 | Concluída e homologada | Registro histórico na retomada de 27/11; sem reexecução nesta missão |
| T03 | Concluída no escopo local | CI remota sobre versão exata ainda sem prova |
| T05 | Concluída no escopo sintético autorizado | Resultados históricos de cinco testes Surefire e três Failsafe; sem reexecução nesta missão |
| T30 | Decisões homologadas | Não equivale a identidade implementada |
| T06 — bootstrap | Homologado pelo usuário | Revalidado no incremento JDBC; V1 preservada |
| T06 — autenticação/sessão | Homologado pelo usuário | Regressões revalidadas no incremento JDBC |
| T06 — integração JDBC | Verificado independentemente e aprovado tecnicamente pelo Auditor, sem bloqueantes; aprovação final pendente | Testador em 28/11 às 06:37:56 −03: 31 Surefire e 12 Failsafe, zero falhas/erros/ignorados, saída 0; PostgreSQL 17.12 e HTTPS real |

## Bloqueios e próximo passo

- JDBC: perfil opt-in atestado com papel somente leitura, V2 de permissões e bootstrap/login/capacidades/recriação de contexto verificados em banco descartável autorizado. Default permanece sem banco; T05 não autentica.
- Execução independente `52e4c8e47103475fabadb79e3ee55d8a`: runner confirmou limpeza, zero contêineres/redes/volumes remanescentes. Rodadas anteriores do Desenvolvedor também confirmaram limpeza.
- Próximo passo: aprovação final expressa do usuário para a integração JDBC. Angular e empresas fora deste incremento.
- O teste `SyntheticPersistenceIT.java` e `RETOMADA-2026-11-27.md` são alterações locais preexistentes preservadas. Não restaurar ou descartar.
- Nenhum banco/Compose/volume existente foi acessado; nenhum commit ou push realizado. Evidências de execução desta entrega ficam na conversa.

## Referências

- [Regras técnicas e fluxo proporcional ao risco](../AGENTS.md).
- [Passagem entre os agentes](../.codex/FLUXO-DA-EQUIPE.md).
- [Requisitos e aceite](requisitos-academicos.md).
- [Decisões, ordem e correspondência T01–T30 / BE-xx](decisoes-e-cronograma.md).
- [Evidências históricas e autorizações anteriores](RETOMADA-2026-11-27.md).

Atualizar este arquivo com tarefa, resultado, bloqueio e próximo passo; no máximo 60 linhas. Não copiar auditorias ou requisitos.
