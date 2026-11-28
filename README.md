# AccountabillitySystem

## Ferramentas

- JDK 21 e Spring Boot 3.5.7. O Maven Wrapper baixa a distribuição 3.9.16 com verificação SHA-256.
- Node.js 26.13.1, npm 11.20.0, Angular/CLI 22.3.0, TypeScript 6.0.3 e RxJS 7.8.2. O `package-lock.json` fixa as dependências, incluindo as variantes nativas Windows e Linux.

No Windows, use o ZIP oficial do Node.js 26.13.1 e confira o SHA-256 publicado antes de extraí-lo, se precisar de uma distribuição portátil. Para ativá-la apenas no terminal atual, acrescente sua pasta ao `PATH` da sessão. Confirme as versões com `java -version`, `node --version` e `npm.cmd --version`.

No IntelliJ IDEA, importe o `pom.xml`, selecione um JDK 21 como SDK do projeto e escolha **Maven Wrapper** em **Build Tools > Maven**. Para executar npm pela IDE, aponte o interpretador Node para o binário 26.13.1 e use `frontend/package.json`.

## Java

Na raiz do repositório, em PowerShell:

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp clean verify
```

No Linux, use `bash ./mvnw` com os mesmos argumentos.

O build produz `target/accountabilitySystem-1.0-SNAPSHOT.jar`. O teste MockMvc standalone valida o contrato do controller `GET /api/status`, com HTTP 200, `text/plain` e corpo `AccountabilitySystem funcionando!`. Durante o teste, a aplicação completa e a camada de persistência ficam fora do contexto.


## Persistência sintética T05

O perfil padrão inicia apenas a fundação web. A configuração não usa URL, usuário ou senha de fallback e não ativa datasource, inicialização SQL, Flyway ou Hibernate. A persistência do Compose preexistente fica desconectada da aplicação até uma tarefa própria definir e autorizar outro perfil. O contrato textual de `GET /api/status` é preservado.

O perfil `t05-sintetico` inicia Flyway com o migrador e aplica Hibernate `validate` com o papel da aplicação. Ambos conferem o recurso Docker atestado e a identidade da conexão antes do uso. Apenas a V1 técnica, o histórico e a tabela `t05_app.probe` fazem parte da cadeia. Flyway mantém baseline automático e clean desabilitados. O usuário da aplicação pode consultar a probe e o marcador, sem DDL nem escrita no histórico.

A verificação padrão usa Surefire sem PostgreSQL. Para a suíte real Failsafe, use PowerShell no Windows com Docker Desktop Linux local, Engine 28 ou superior, e autorização para provisionar a imagem oficial, redes bridge dedicadas e contêineres efêmeros. A validação exige publicação em `127.0.0.1` com portas dinâmicas e dados em tmpfs. O script confere o contexto, os IDs e as labels antes de conectar e remove somente os recursos que criou. A imagem baixada fica no cache do Docker.

O script procura `docker.exe` primeiro no diretório padrão do Docker Desktop e depois no `PATH`. O contexto `desktop-linux` precisa apontar para o pipe local do Docker Desktop antes de qualquer consulta ao engine.

Cada rede regulada pelo adendo de T05 é uma bridge dedicada, etiquetada e sem `--internal`. A publicação em loopback precisa ser comprovada pela configuração do contêiner e pelo mapeamento ativo do engine. Se a combinação autorizada não produzir o binding exigido, o script aborta antes do bootstrap SQL e tenta limpar os recursos identificados.

```powershell
.\mvnw.cmd -B -ntp clean verify
.\scripts\t05\pg17.ps1 -Action Verify
```

O segundo comando provisiona destinos distintos para o fluxo normal, divergência e rollback. Em seguida, executa `mvnw.cmd -B -ntp -Pt05-postgres-it verify` e tenta limpar os recursos identificados. O lançador isola opções genéricas de configuração Spring, servidor, gerenciamento, Java e Maven do processo de teste e restaura as variáveis ao sair. O perfil Maven deve falhar caso seja invocado sem a identidade da execução. Se houver falha de limpeza, preserve os IDs do relatório e confira suas labels antes de remover qualquer recurso. O comando dispensa Docker Compose, volumes de disco e credenciais preexistentes.

Os relatórios ficam em `target/surefire-reports` e `target/failsafe-reports`. O teste operacional lê `/actuator/health/readiness` pela porta de gerenciamento efêmera em loopback. Uma interrupção do banco deve retornar HTTP 503 enquanto o status textual segue disponível pela porta HTTP da aplicação. O workflow de T03 continua executando a verificação padrão.

Em engines que ocultam o mapeamento ativo durante a pausa, a identidade e a rede do contêiner continuam atestadas, mas o JDBC fica bloqueado. A retomada exige essa mesma identidade e revalida o binding ativo antes de liberar conexões novamente.

## Bootstrap sintético T06

O primeiro incremento cria somente o administrador inicial, com Argon2id (19.456 KiB, duas iterações, paralelismo 1, salt de 16 bytes e hash de 32 bytes), capacidades globais e auditoria. O encerramento é transacional e definitivo. Nenhum bootstrap é registrado no startup Spring; login, sessão e Angular continuam fora deste incremento.

Com autorização específica e Docker Desktop Linux local, execute na raiz `powershell.exe -NoProfile -NonInteractive -File scripts/t06/pg17.ps1 -Action Verify`. O runner cria uma rede e um PostgreSQL 17 exclusivos, com tmpfs, porta dinâmica em loopback e sem volumes persistentes; aplica somente migrações T06, executa testes e remove os próprios recursos após conferir IDs e labels. O perfil `t06-postgres-it` falha antes de JDBC sem os metadados atestados do runner. As fixtures são fictícias; testes de rollback podem resetar somente esse banco descartável, usando o papel proprietário separado do papel de bootstrap.

`org.example.identidade.BootstrapCommand --bootstrap-sintetico` é a entrada local explícita, com console obrigatório, senha sem eco e confirmação da verificação externa do operador/destinatário. Requer metadados e credenciais do destino sintético atestado; não aceita URL JDBC livre nem prepara banco. Identificação, referência da autorização/verificação e data ficam na auditoria, sem senha. O executável padrão continua usando `org.example.Main`. Operação real continua sem autorização.

## Autenticação e sessão T06

A API de identidade exige HTTPS. `GET /api/v1/sessao` retorna estado, identificador/capacidades quando autenticado e `csrfToken`/`csrfHeader`, com `Cache-Control: no-store`. Envie o token no cabeçalho `X-CSRF-TOKEN` em toda mutação, inclusive `POST /api/v1/login` e `POST /api/v1/logout`. Login recebe formulário `application/x-www-form-urlencoded` com `identificador` e `senha`, somente no corpo; sucesso retorna 204 e falha de credenciais retorna 401 genérico. Logout retorna 204. Após login/logout, consulte novamente a sessão para obter novo token; após expiração/401 ou CSRF inválido/403, obtenha o estado e solicite nova autenticação, sem repetir automaticamente a operação anterior.

`JSESSIONID` permanece apenas no cookie `HttpOnly; Secure; SameSite=Lax`; o servidor troca o ID no login, invalida no logout e expira após 30 minutos de inatividade. Não há sessão em URL, HTTP Basic ou usuário/senha gerados automaticamente. Sem datasource T06 configurado, autenticação falha fechada.

O perfil opt-in `t06-sintetico` conecta a autenticação JDBC exclusivamente ao destino atestado do runner T06, usando `t06_identity_reader`, com leitura de identidade/capacidades e sem escrita, DDL, acesso à auditoria ou poderes de bootstrap. A V2 concede somente essa leitura; V1 permanece intacta. Esse perfil não executa migração/bootstrap no startup nem inicializa repositórios JPA. O runner verifica bootstrap persistido → login HTTPS → capacidades/sessão e recriação do contexto sobre o mesmo banco descartável. O perfil padrão continua sem datasource; T05 não é destino de autenticação.

O limitador local admite até cinco falhas por identificador/IP e 30 por IP em janela de 15 minutos, contando tentativas em andamento para controlar concorrência. Retorna 429 com `Retry-After: 900`. A memória tem limite de 10 mil entradas; saturação recusa novas origens até expiração, sem descartar limites ativos. Sessão e limitador são locais ao processo, não compartilhados entre instâncias. O IP é o remoto do socket; esta entrega usa HTTPS direto, sem configuração de proxy confiável.

`mvnw.cmd -B -ntp test` executa fixtures fictícias, HTTPS embarcado em loopback/porta efêmera e certificado temporário confiado somente pelo cliente de teste. Os testes conferem timeout de 1.800 segundos e expiração real do servlet com intervalo reduzido para um segundo apenas na fixture. Não executam Docker, migrações ou PostgreSQL.

## Angular

Em PowerShell, a partir da raiz:

```powershell
Set-Location frontend
npm.cmd ci --engine-strict
npm.cmd run test:ci
npm.cmd run build
```

No Linux, use `cd frontend` e `npm` para os mesmos comandos. O teste usa Vitest com DOM simulado. O build de produção gera `frontend/dist/accountability/browser/`. Para executar o frontend de fundação localmente, use `npm.cmd start` no Windows ou `npm start` no Linux.

## Integração contínua

O workflow `Fundação` executa os jobs Java e Angular em runners Ubuntu 24.04. Cada job compila, testa e verifica sua saída. O job `Gate de entrega` confirma sucesso apenas quando os dois terminam com êxito. Uma falha de instalação, teste, build ou verificação de saída bloqueia esse job. A CI executa apenas a fundação e termina no gate.
