# Fluxo simplificado da equipe

As regras comuns, autorizações, critérios de risco e evidências estão em [AGENTS.md](../AGENTS.md). Este arquivo descreve somente a passagem operacional.

1. A Coordenadora consulta o [andamento](../docs/andamento.md) e o escopo autorizado, delimita tarefa, aceite e arquivos exclusivos e aplica os gatilhos de AGENTS.md.
2. Quando houver decisão ou dependência indefinida, aciona Planejador para um plano curto e obtém aprovação explícita. Caso contrário, encaminha a tarefa pequena já aprovada diretamente ao Desenvolvedor.
3. Desenvolvedor implementa e executa as verificações pertinentes. Encerra a escrita antes da passagem independente.
4. Quando exigido pelo risco, Testador recebe versão estável e verifica o aceite. Falhas voltam ao Desenvolvedor com reprodução, esperado e observado; correções recebem nova verificação pertinente.
5. Quando exigido pelo risco ou marco, Auditor realiza revisão focal da mudança e das evidências atuais. Achados voltam ao responsável, sem reabrir auditorias gerais.
6. Coordenadora apresenta relatório curto e aguarda aprovação final explícita. Somente então registra a entrega como concluída e segue para a próxima tarefa autorizada.

Estados aplicáveis: decisão/plano pendente → aprovação do escopo → implementação → verificação → revisão focal, quando acionada → aprovação final → concluída. Uma tarefa já aprovada não repete a aprovação do escopo; etapas condicionais não são exigidas por rotina.

## Papéis e passagem

| Papel | Responsabilidade exclusiva |
|---|---|
| Coordenadora | Delimitar, delegar, acompanhar devoluções e apresentar aprovação final |
| Planejador | Resolver decisões relevantes com plano curto, em somente leitura |
| Desenvolvedor | Editar arquivos atribuídos e corrigir falhas dentro do escopo aprovado |
| Testador | Verificar independentemente e editar apenas testes atribuídos; não corrigir produção |
| Auditor | Emitir parecer focal em somente leitura; não substituir testes nem aprovação do usuário |

A passagem fica na conversa, com os dados definidos em AGENTS.md. O [cronograma](../docs/decisoes-e-cronograma.md) liga decisões aos backlogs históricos; não criar relatórios duplicados.

## Ativação da transição v1.1

Esta transição documental foi aprovada para Desenvolvedor → Testador → Auditor focal → aprovação final. Não inclui T06. Após aprovação final, uma nova sessão carrega a configuração simplificada e retoma a próxima tarefa indicada no andamento. Editar arquivos não substitui instruções já carregadas na sessão atual.

As versões anteriores permanecem recuperáveis pelo commit indicado no cronograma. Não restaurá-las sobre o trabalho local.