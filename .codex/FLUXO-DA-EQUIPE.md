# Fluxo da equipe de agentes

## Coordenacao e etapas
A sessão principal atua como coordenadora. Leia o AGENTS.md da raiz e este documento.
Distribua tarefas aos quatro agentes exclusivamente conforme suas responsabilidades.
Nao assuma automaticamente funcoes especializadas se um agente estiver indisponivel:
informe o impedimento e mantenha a etapa pendente. Somente a coordenadora delega;
os especialistas não criam subagentes. Registre na conversa tarefa, responsável,
versão do plano, aprovação, arquivos exclusivos, resultados e próximo responsável.

1.
Planejador analisa o escopo solicitado e apresenta plano versionado com IDs de
tarefas pequenas, dependências, arquivos previstos e critérios de aceite.
2. Coordenadora apresenta o plano e aguarda aprovação explícita do usuário.
   Silêncio, aprovação da configuração ou parecer técnico não autorizam implementação.
3. Desenvolvedor recebe somente tarefas aprovadas, evidência da aprovação, critérios
   e atribuição exclusiva de arquivos. Alterações são pequenas e rastreáveis.
4. Após encerradas as edições, Testador recebe a versão estável e cria/executa testes.
   Falhas voltam ao Desenvolvedor via coordenação com reprodução, esperado e observado.
   Correções voltam ao Testador para nova verificação.
5. Auditor revisa independentemente plano, alterações e evidências atuais. Emite
   APROVADO TECNICAMENTE ou REPROVADO TECNICAMENTE com justificativa. Problemas
   retornam ao agente responsável; mudanças no plano exigem nova aprovação do usuário.
6. Coordenadora apresenta entrega, verificações, limitações e parecer técnico e
   aguarda aprovação final explícita do usuário. Só então a entrega fica concluída.

Estados: EM PLANEJAMENTO ? AGUARDANDO APROVAÇÃO DO PLANO ? EM IMPLEMENTAÇÃO ?
EM TESTE ? EM AUDITORIA ? AGUARDANDO APROVAÇÃO FINAL ? CONCLUÍDA.
Devoluções reabrem a etapa responsável e invalidam evidências afetadas.

## Regras comuns
Todos obedecem ao AGENTS.md. Preserve AGENTS.md, arquivos existentes, trabalho alheio,
PostgreSQL, volumes e credenciais. Não leia, compartilhe ou registre conteúdo de .env,
senhas, tokens ou dados empresariais sensíveis; sanitize logs e evidências.
Não altere dados contábeis reais nem apague volumes Docker. Não faça commits, pushes,
migrações ou alterações destrutivas sem autorização específica. A aprovação de tarefas
não implica autorização dessas operações. Não use ferramentas MCP para contornar regras.
Não inicie aplicação, Compose ou testes contra banco real ou compartilhado.
Persistência exige PostgreSQL isolado de teste e destino comprovado; ddl-auto=update
pode alterar esquema ao iniciar Spring. Se não comprovar isolamento, não execute.
Planejador e Auditor não editam arquivos; Testador edita apenas testes atribuídos e
não corrige produção; Desenvolvedor não se aprova. Relatórios ficam na conversa;
persistência de novos documentos depende do escopo autorizado.

## Exclusividade e isolamento
Nunca execute agentes que modifiquem os mesmos arquivos simultaneamente. Prefira
etapas sequenciais; encerre escritores antes de testes e auditoria. Registre os arquivos
atribuídos e a versão/hash analisada. Interrompa escrita em caso de conflito.
CLI 0.166.0 suporta --worktree. Em tarefas futuras, use worktree por tarefa de escrita
quando adequado, sem criar agora nem descartar alterações automaticamente. Worktree não
isola banco, serviços ou credenciais. Confira que AGENTS.md e .codex/ estejam presentes:
arquivos não versionados não são transportados automaticamente; providencie cópia
somente dentro do escopo autorizado.
Agentes personalizados herdam sandbox, aprovações e MCP da sessão principal. Restrições
de papel são instruções, não ACLs de arquivos ou sandboxes independentes. Para leitura
tecnicamente restrita no shell, execute Planejador/Auditor em sessões separadas com
--sandbox read-only, respeitando também permissões das ferramentas MCP.
O limite do ambiente prevalece. Quatro papéis não exigem quatro subagentes simultâneos.

## Evidências
Cada passagem identifica plano aprovado, tarefa, arquivos, versão/hashes, critérios
atendidos e pendências. Testador registra comandos, diretório, data, códigos de saída,
totais, falhas e ignorados. Auditor confere evidências contra a versão atual.
Nunca invente resultados nem declare executados testes apenas escritos ou propostos.

## Primeiro ciclo
Abra uma nova sessão na raiz após carregar a configuração e solicite:

> Coordene a equipe conforme .codex/FLUXO-DA-EQUIPE.md. Acione somente o Planejador
> para analisar [objetivo e limites], apresentar tarefas pequenas e critérios de
> aceite e aguardar minha aprovação. Não implemente ainda.

A configuração disponibiliza papéis; não inicia agentes automaticamente nem implementa
uma máquina de estados externa. A coordenadora aplica as transições por instruções e
aprovações explícitas. Carregar a configuração não autoriza planejar ou desenvolver.