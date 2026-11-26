param(
    [ValidateSet('Verify','Attest')][string]$Action = 'Verify',
    [string]$MavenSettings,
    [string]$MavenRepository
)
$ErrorActionPreference = 'Stop'
$context = 'desktop-linux'
$label = 'org.example.accountability.t06.run'
$storage = '/var/lib/postgresql/data'
$docker = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $docker -PathType Leaf)) {
    $dockerCommand = Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $dockerCommand) { throw 'Docker Desktop indisponivel: docker.exe ausente no local padrao e PATH.' }
    $docker = $dockerCommand.Path
}

function Invoke-Docker {
    $output = & $docker --context $context @args 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Operacao Docker T06 falhou.' }
    $output
}

function Assert-Context {
    if ($env:DOCKER_HOST -or $env:DOCKER_CONFIG -or ($env:DOCKER_CONTEXT -and $env:DOCKER_CONTEXT -ne $context)) {
        throw 'Ambiente Docker redirecionado.'
    }
    if ((Invoke-Docker context inspect $context --format '{{.Endpoints.docker.Host}}') -cne 'npipe:////./pipe/dockerDesktopLinuxEngine') {
        throw 'Contexto Docker nao e local.'
    }
    if ((Invoke-Docker info --format '{{.OSType}}|{{.OperatingSystem}}') -cne 'linux|Docker Desktop') { throw 'Engine divergente.' }
    $version = Invoke-Docker version --format '{{.Server.Version}}'
    if ($version -notmatch '^(\d+)\.' -or [int]$Matches[1] -lt 28) { throw 'Engine 28 ou superior obrigatorio para isolamento loopback.' }
}

function Assert-Target($target, [switch]$BeforeStart) {
    if ($target.RUN -cnotmatch '^[0-9a-f]{32}$' -or $target.CONTAINER -cnotmatch '^[0-9a-f]{64}$' -or
        $target.NETWORK -cnotmatch '^[0-9a-f]{64}$' -or $target.IMAGE -cnotmatch '^docker\.io/library/postgres@sha256:[0-9a-f]{64}$') {
        throw 'Metadados T06 invalidos.'
    }
    $id = $target.CONTAINER
    $identity = Invoke-Docker inspect --type container --format '{{.Id}}|{{.Name}}|{{.Config.Image}}' $id
    $labels = Invoke-Docker inspect --type container --format '{{json .Config.Labels}}' $id | ConvertFrom-Json
    $mounts = @(Invoke-Docker inspect --type container --format '{{json .Mounts}}' $id | ConvertFrom-Json)
    $configured = @(Invoke-Docker inspect --type container --format '{{json .HostConfig.Mounts}}' $id | ConvertFrom-Json)
    $bindings = Invoke-Docker inspect --type container --format '{{json .HostConfig.PortBindings}}' $id | ConvertFrom-Json
    $bound = @($bindings.'5432/tcp')
    $networkIdentity = Invoke-Docker network inspect --format '{{.ID}}|{{.Name}}|{{.Driver}}|{{.Internal}}' $target.NETWORK
    $networkLabels = Invoke-Docker network inspect --format '{{json .Labels}}' $target.NETWORK | ConvertFrom-Json
    $mode = Invoke-Docker inspect --type container --format '{{.HostConfig.NetworkMode}}' $id
    if ($identity -cne "$id|/accountability-t06-pg-$($target.RUN)|$($target.IMAGE)" -or $labels.$label -cne $target.RUN -or
        $labels.PSObject.Properties['com.docker.compose.project'] -or
        $networkIdentity -cne "$($target.NETWORK)|accountability-t06-net-$($target.RUN)|bridge|false" -or
        $networkLabels.$label -cne $target.RUN -or $networkLabels.PSObject.Properties['com.docker.compose.project'] -or
        $mode -cnotin @($target.NETWORK, "accountability-t06-net-$($target.RUN)") -or
        $configured.Count -ne 1 -or $configured[0].Type -cne 'tmpfs' -or $configured[0].Target -cne $storage -or
        @($mounts | Where-Object { $_.Type -and ($_.Type -cne 'tmpfs' -or $_.Destination -cne $storage) }).Count -ne 0 -or
        @($bindings.PSObject.Properties).Count -ne 1 -or $bound.Count -ne 1 -or $bound[0].HostIp -cne '127.0.0.1') {
        throw 'Isolamento T06 nao comprovado.'
    }
    if ($BeforeStart) {
        if ($bound[0].HostPort -cnotin @('', '0')) { throw 'Porta deve ser dinamica.' }
        return
    }
    $attached = Invoke-Docker inspect --type container --format '{{json .NetworkSettings.Networks}}' $id | ConvertFrom-Json
    $networks = @($attached.PSObject.Properties)
    $members = Invoke-Docker network inspect --format '{{json .Containers}}' $target.NETWORK | ConvertFrom-Json
    $memberIds = @($members.PSObject.Properties.Name)
    if ($networks.Count -ne 1 -or $networks[0].Value.NetworkID -cne $target.NETWORK -or
        $memberIds.Count -ne 1 -or $memberIds[0] -cne $id -or
        (Invoke-Docker inspect --type container --format '{{.State.Running}}|{{.State.Paused}}' $id) -cne 'true|false' -or
        $target.PORT -cnotmatch '^[1-9][0-9]{0,4}$' -or [int]$target.PORT -eq 5432 -or [int]$target.PORT -gt 65535 -or
        (Invoke-Docker port $id 5432/tcp) -cne "127.0.0.1:$($target.PORT)") { throw 'Destino ativo T06 divergente.' }
}

function New-Hex {
    $buffer = [byte[]]::new(32)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($buffer) } finally { $random.Dispose() }
    [BitConverter]::ToString($buffer).Replace('-', '').ToLowerInvariant()
}

function Remove-Target($target) {
    Assert-Context
    if ($target.CONTAINER) {
        $identity = Invoke-Docker inspect --type container --format '{{.Id}}|{{.Name}}' $target.CONTAINER
        $labels = Invoke-Docker inspect --type container --format '{{json .Config.Labels}}' $target.CONTAINER | ConvertFrom-Json
        if ($identity -cne "$($target.CONTAINER)|/accountability-t06-pg-$($target.RUN)" -or $labels.$label -cne $target.RUN) {
            throw 'Identidade de limpeza do container divergente.'
        }
        $null = Invoke-Docker rm --force $target.CONTAINER
        Write-Host "T06 removeu container $($target.CONTAINER)."
    }
    if ($target.NETWORK) {
        $identity = Invoke-Docker network inspect --format '{{.ID}}|{{.Name}}' $target.NETWORK
        $labels = Invoke-Docker network inspect --format '{{json .Labels}}' $target.NETWORK | ConvertFrom-Json
        if ($identity -cne "$($target.NETWORK)|accountability-t06-net-$($target.RUN)" -or $labels.$label -cne $target.RUN) {
            throw 'Identidade de limpeza da rede divergente.'
        }
        $null = Invoke-Docker network rm $target.NETWORK
        Write-Host "T06 removeu rede $($target.NETWORK)."
    }
    $filter = "label=$label=$($target.RUN)"
    $containers = @(Invoke-Docker container ls -aq --filter $filter)
    $networks = @(Invoke-Docker network ls -q --filter $filter)
    $volumes = @(Invoke-Docker volume ls -q --filter $filter)
    if ($containers.Count + $networks.Count + $volumes.Count -ne 0) { throw 'Recursos T06 remanescentes apos limpeza.' }
    Write-Host "T06 execucao $($target.RUN): remanescentes containers=0 redes=0 volumes=0."
}

Assert-Context
if ($Action -eq 'Attest') {
    Assert-Target @{RUN=$env:T06_RUN; CONTAINER=$env:T06_CONTAINER; NETWORK=$env:T06_NETWORK; IMAGE=$env:T06_IMAGE; PORT=$env:T06_PORT}
    exit 0
}

$run = [guid]::NewGuid().ToString('N')
$target = @{RUN=$run; DATABASE="acct_t06_$run"; MIGRATION_PASSWORD=(New-Hex); BOOTSTRAP_PASSWORD=(New-Hex); READER_PASSWORD=(New-Hex)}
$saved = @{}
$locationChanged = $false
function Set-SavedVariable($name, $value) {
    if (-not $saved.ContainsKey($name)) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
try {
    foreach ($name in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
        if ($name -match '^(SPRING|SERVER|MANAGEMENT|T06)[_.]' -or $name -match '^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_OPTS|MAVEN_ARGS)$') {
            Set-SavedVariable $name $null
        }
    }
    $null = Invoke-Docker pull docker.io/library/postgres:17-alpine
    $digests = @(Invoke-Docker image inspect --format '{{json .RepoDigests}}' docker.io/library/postgres:17-alpine | ConvertFrom-Json |
        Where-Object { $_ -cmatch '^(docker\.io/library/|library/)?postgres@sha256:[0-9a-f]{64}$' })
    if ($digests.Count -ne 1) { throw 'Digest PostgreSQL ambiguo.' }
    $target.IMAGE = 'docker.io/library/postgres@' + ($digests[0] -split '@', 2)[1]
    $target.NETWORK = Invoke-Docker network create --driver bridge --label "$label=$run" "accountability-t06-net-$run"
    Set-SavedVariable 'POSTGRES_PASSWORD' $target.MIGRATION_PASSWORD
    Set-SavedVariable 'POSTGRES_DB' $target.DATABASE
    $target.CONTAINER = Invoke-Docker create --name "accountability-t06-pg-$run" --label "$label=$run" --network $target.NETWORK `
        --mount "type=tmpfs,destination=$storage" --publish '127.0.0.1::5432' --env POSTGRES_PASSWORD --env POSTGRES_DB $target.IMAGE
    Assert-Target $target -BeforeStart
    Write-Host "T06 execucao $run; imagem $($target.IMAGE); container $($target.CONTAINER); rede $($target.NETWORK)."
    $null = Invoke-Docker start $target.CONTAINER
    $mapping = Invoke-Docker port $target.CONTAINER 5432/tcp
    if ($mapping -cnotmatch '^127\.0\.0\.1:([1-9][0-9]{0,4})$') { throw 'Porta loopback ausente.' }
    $target.PORT = $Matches[1]
    Assert-Target $target
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        & $docker --context $context exec $target.CONTAINER pg_isready -q -h 127.0.0.1 -d $target.DATABASE 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL nao inicializou.' }
    Assert-Target $target
    $sql = "CREATE ROLE t06_bootstrap LOGIN PASSWORD '$($target.BOOTSTRAP_PASSWORD)'; CREATE ROLE t06_identity_reader LOGIN PASSWORD '$($target.READER_PASSWORD)'; REVOKE ALL ON DATABASE $($target.DATABASE) FROM PUBLIC; GRANT CONNECT ON DATABASE $($target.DATABASE) TO t06_bootstrap, t06_identity_reader; REVOKE CREATE ON SCHEMA public FROM PUBLIC; CREATE TABLE public.t06_target (run text PRIMARY KEY); INSERT INTO public.t06_target VALUES ('$run'); GRANT SELECT ON public.t06_target TO t06_bootstrap, t06_identity_reader;"
    $sql | & $docker --context $context exec -i $target.CONTAINER psql -X -q -v ON_ERROR_STOP=1 -1 -f - -U postgres -d $target.DATABASE 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Preparacao sintética falhou.' }
    $sql = $null
    foreach ($key in $target.Keys) { Set-SavedVariable ('T06_' + $key) $target[$key] }
    Push-Location (Join-Path $PSScriptRoot '..\..')
    $locationChanged = $true
    $mavenArguments = @('-B','-ntp','-C','-Pt06-postgres-it','verify')
    if ($MavenSettings) { $mavenArguments += @('-s',$MavenSettings,'-gs',$MavenSettings) }
    if ($MavenRepository) { $mavenArguments += "-Dmaven.repo.local=$MavenRepository" }
    & .\mvnw.cmd @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Verificacao T06 falhou.' }
} finally {
    if ($locationChanged) { Pop-Location }
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    Remove-Target $target
}
