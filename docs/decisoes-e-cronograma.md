# Decisões e ordem de implementação

## Uso e fontes

Esta é a referência operacional de decisões, prioridades e correspondências entre backlogs. [Requisitos](requisitos-academicos.md) definem aceite; [andamento](andamento.md) registra progresso; [AGENTS.md](../AGENTS.md) define regras técnicas. Não repetir resultados de testes ou aprovações de entrega aqui.

Fontes: aprovação expressa da transição v1.1 e complementos Clean Code em 27/11/2026; [retomada de 27/11](RETOMADA-2026-11-27.md), que registra decisões T30 e a cadeia aprovada; H.2 da v1.5 preservada em `75285c9:planejamento.md` (blob `3440eedd0a6a0c4028f19dd1183c79c26e537b38`). O [planejamento do checkout](../planejamento.md) é outra versão, com ARQ/DB/BE, e o [plano funcional](PLANO_DESENVOLVIMENTO.md) usa T01–T44; IDs desses documentos não são intercambiáveis.

## Decisões aprovadas

- **Arquitetura:** tecnologias e monólito modular definidos em AGENTS.md; regras de negócio no backend. Opções antigas de React, Vue ou Thymeleaf não substituem Angular aprovado.
- **T30 — identidade:** local; usuário aprovou para T06 Argon2id com 19.456 KiB, duas iterações, paralelismo 1, salt aleatório de 16 bytes e hash de 32 bytes, usando biblioteca consolidada compatível com Spring Security. Convites aleatórios, uso único, validade de 24 horas, somente hash persistido e consumo atômico; entrega individual a destinatário verificado, sem presumir serviço de e-mail.
- **T30 — sessão:** Angular/API na mesma origem, sessão no servidor, `JSESSIONID` com `HttpOnly`, `Secure`, `SameSite=Lax`; rotação no login, invalidação no logout e 30 minutos de inatividade. CSRF nas mutações, inclusive login/logout, contexto em `GET /api/v1/sessao`, limitação de tentativas e erro genérico. Testes sintéticos precisam de HTTPS; detalhar token CSRF e expiração sem expor cookie de sessão ao JavaScript.
- **T30 — bootstrap:** comando local com autorização própria, nunca automático no startup; operador/destinatário verificados, um administrador inicial com unicidade e proteção transacional; capacidades `ADMINISTRAR_IDENTIDADES` e `CRIAR_EMPRESA`; auditoria e encerramento definitivo na mesma transação.
- **T06 — delta sessão aprovado:** CSRF via JSON de sessão e cabeçalho `X-CSRF-TOKEN`, renovado após login/logout; expiração exige nova autenticação sem replay automático. Até cinco falhas por identificador/IP e 30 por IP em 15 minutos, com reservas concorrentes, expiração e memória limitada. Sessão/limitador locais, HTTPS direto; sem confiança em proxy encaminhador. Incremento sem Docker, PostgreSQL, migrações, Angular ou empresas; integração JDBC permanece não verificada.
- **T30 — delegação:** concessão global por ator autorizado a outro destinatário ativo; preservar último administrador; conferir vínculo/permissões no servidor; fundador `ADMIN` limitado à empresa criada; impedir autoconcessão e escalada.
- **Transição v1.1:** implementar somente os dez arquivos aprovados, validar documentalmente, revisão focal e aprovação final. Não iniciar T06 nesta missão. As instruções já carregadas na sessão continuam válidas; o fluxo simplificado será carregado em nova sessão após aprovação final.

## MVP e sequência

**Prioridade aprovada:** `T05 + T30 → T06 → T07 → T08/T09`. T04/T28 e banco legado não são pré-requisitos de T06. Fundação sintética T05 não autoriza novas migrações ou infraestrutura T06.

T06 deve avançar em incrementos pequenos: **A**, identidade, bootstrap, login/sessão/logout/CSRF e cliente Angular; **B**, convites, ativação, capacidades globais e criação atômica de empresa/vínculo. Resolver somente decisões técnicas necessárias ao incremento; preservar decisões T30 já homologadas. Aprovação da transição não autoriza esses efeitos operacionais nem dispensa autorizações específicas.

**MVP histórico proposto, sem aprovação integral comprovada:** autenticação e empresas, plano/modelo, calendário, lançamentos, estorno, abertura, Diário/Razão/Balancete e interface correspondente, com auditoria, migrações e testes. BP/DRE no primeiro MVP continuam condicionados à decisão de produto e homologação. Não promover essa proposta a escopo integral autorizado.

Após a cadeia aprovada, a v1.5 orienta dependências dos domínios: contas/calendário → motor → abertura/livros → vendas → demonstrações → exportações → homologação. Não é uma autorização em bloco. Datas de entrega não foram aprovadas; o cronograma é uma ordem por dependências.

## Matriz de correspondência histórica

Usar **T01–T30 da v1.5** para acompanhamento deste ciclo. A coluna do checkout registra aproximações sem declarar equivalência total; tarefas sem ID correspondente continuam com o T original. Não restaurar checkout ou renumerar históricos para obter essa correspondência.

| v1.5 | Entrega | Relação com H.2 do checkout |
|---|---|---|
| T01 | Escopo e evidências | ARQ-01, correspondência temática |
| T02 | Toolchain e lockfiles | Sem ID específico |
| T03 | Build e CI Java/Angular | Sem ID específico |
| T04 | Destino e recuperação autorizada | DB-01 + DB-02, parcial |
| T05 | Flyway sintético e prontidão | DB-03, parcial: não herda dependência de legado |
| T06 | Bootstrap, sessão, identidades e empresa | BE-03 + BE-04, parcial: não cobrem todo bootstrap, provisionamento e idempotência |
| T07 | Revogação, autorização e auditoria | BE-04 tangencia vínculo; demais controles sem ID específico |
| T08 | Plano, hierarquia e modelo | Sem ID específico; continuação prevista |
| T09 | Calendário e chaves compostas | BE-02 tangencia datas; não cobre calendário persistido |
| T10 | Parser, armazenamento e lease | Sem ID específico |
| T11 | Importar/exportar plano | Sem ID específico |
| T12 | Rascunho, composição e interface | BE-02 cobre somente apoio monetário/data |
| T13 | Contabilização e proteção persistida | Sem ID específico; BE-01 é apoio de contrato, não motor |
| T14 | Fechar/reabrir e estornar | Sem ID específico |
| T15 | Abertura e substituição atômica | Sem ID específico |
| T16 | Snapshot, Diário e paginação | Sem ID específico |
| T17 | Razão e Balancete | Sem ID específico |
| T18 | Importação de vendas | Sem ID específico |
| T19 | Consolidação e retificação de vendas | Sem ID específico |
| T20 | Políticas, Balanço e DRE | Sem ID específico |
| T21 | Classificação e DFC direta | Sem ID específico |
| T22 | Encerramento e reabertura anual | Sem ID específico |
| T23 | DFC indireta condicionada | Sem ID específico |
| T24 | Fila e PDF de snapshot | Sem ID específico |
| T25 | XLSX seguro | Sem ID específico |
| T26 | Integração de telas/acessibilidade | Sem ID específico; ARQ-01 fornece parte da referência |
| T27 | Carga, recuperação e homologação | DB-02 tangencia recuperação; não equivale à homologação |
| T28 | Adoção opcional de legado | DB-01/02/03 tangenciam preparação; nenhuma equivalência integral |
| T29 | Políticas contábeis e funcionais | ARQ-02, parcial |
| T30 | Identidade e autorização | ARQ-03, somente parte das decisões técnicas |

BE-01 (erros/validação) e BE-02 (dinheiro/datas) são apoios transversais do checkout, não renomeações de T01/T02. ARQ-03 também contém decisões arquiteturais além de T30. Para contratos detalhados ou dependências além da cadeia atual, consultar somente a seção pertinente da v1.5 com `git show 75285c9:planejamento.md`.

## Pendências delimitadas e preservação

- T06 ainda requer delimitação técnica dos incrementos, parâmetros de segurança, migrações e ambiente sintético próprios; aplicar os gatilhos de planejamento de AGENTS.md somente às decisões faltantes.
- Políticas contábeis, layouts, DFC indireta, limites operacionais e recuperação dependem de homologação do domínio. M03 permanece sem referência visual validada. Não reabrir auditoria geral para iniciar identidade.
- [Planejamento](../planejamento.md), [plano funcional](PLANO_DESENVOLVIMENTO.md) e [retomadas de 25/11](RETOMADA-2026-11-25.md) e [27/11](RETOMADA-2026-11-27.md) permanecem integrais como histórico. Suas mensagens de retomada antigas não substituem o fluxo vigente.
- As sete regras anteriores à transição estão preservadas no commit `c92de6438caa1b878e18f8b70da7542c1b3f664f`: `AGENTS.md`, `.codex/config.toml`, `.codex/FLUXO-DA-EQUIPE.md` e os quatro `.codex/agents/*.toml`. Consultar um arquivo com `git show c92de6438caa1b878e18f8b70da7542c1b3f664f:<caminho>`, sem restaurar nem alterar HEAD.
- [Lex](https://github.com/HsFenrir/lex/blob/main/docs/decisoes-e-cronograma.md) foi referência de separação documental e acompanhamento; nenhuma arquitetura ou decisão de produto foi importada.
