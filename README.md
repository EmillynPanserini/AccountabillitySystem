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
