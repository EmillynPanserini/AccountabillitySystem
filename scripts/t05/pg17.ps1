param([Parameter(Mandatory=$true)][ValidateSet('Verify','Attest','Pause','Resume')][string]$Action)
$ErrorActionPreference = 'Stop'
$context = 'desktop-linux'
$label = 'org.example.accountability.t05.run'
$storage = '/var/lib/postgresql/data'
$docker = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $docker -PathType Leaf)) {
    $dockerCommand = Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $dockerCommand) { throw 'Executavel docker.exe indisponivel.' }
    $docker = $dockerCommand.Path
}

function Invoke-Docker {
    $output = & $docker --context $context @args 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Operacao Docker T05 falhou.' }
    $output
}

function Assert-Context {
    if ($env:DOCKER_HOST -or $env:DOCKER_CONFIG -or ($env:DOCKER_CONTEXT -and $env:DOCKER_CONTEXT -ne $context)) {
        throw 'Ambiente Docker redirecionado.'
    }
    $endpoint = Invoke-Docker context inspect $context --format '{{.Endpoints.docker.Host}}'
    if ($endpoint -cne 'npipe:////./pipe/dockerDesktopLinuxEngine') {
        throw 'O contexto desktop-linux nao aponta para o pipe local do Docker Desktop.'
    }
    $engine = Invoke-Docker info --format '{{.OSType}}|{{.OperatingSystem}}'
    $version = Invoke-Docker version --format '{{.Server.Version}}'
    if ($engine -cne 'linux|Docker Desktop' -or
        $version -notmatch '^(\d+)\.' -or [int]$Matches[1] -lt 28) {
        throw 'T05 requer o engine Linux local do Docker Desktop, versao 28 ou superior.'
    }
}

function Assert-Container($target, [switch]$BeforeStart, [switch]$AllowPaused) {
    if ($target.RUN -cnotmatch '^[0-9a-f]{32}$' -or $target.CONTAINER -cnotmatch '^[0-9a-f]{64}$' -or
        $target.NETWORK -cnotmatch '^[0-9a-f]{64}$' -or $target.IMAGE -cnotmatch '^docker\.io/library/postgres@sha256:[0-9a-f]{64}$') {
        throw 'Identidade Docker T05 invalida.'
    }
    $id = $target.CONTAINER
    $identity = Invoke-Docker inspect --type container --format '{{.Id}}|{{.Name}}|{{.Config.Image}}' $id
    $labels = Invoke-Docker inspect --type container --format '{{json .Config.Labels}}' $id | ConvertFrom-Json
    $tmpfs = @(Invoke-Docker inspect --type container --format '{{json .HostConfig.Mounts}}' $id | ConvertFrom-Json)
    $mounts = @(Invoke-Docker inspect --type container --format '{{json .Mounts}}' $id | ConvertFrom-Json)
    $bindings = Invoke-Docker inspect --type container --format '{{json .HostConfig.PortBindings}}' $id | ConvertFrom-Json
    $bound = @($bindings.'5432/tcp')
    $mode = Invoke-Docker inspect --type container --format '{{.HostConfig.NetworkMode}}' $id
    $network = Invoke-Docker network inspect --format '{{.ID}}|{{.Name}}|{{.Driver}}|{{.Internal}}' $target.NETWORK
    $networkLabels = Invoke-Docker network inspect --format '{{json .Labels}}' $target.NETWORK | ConvertFrom-Json
    if ($identity -cne "$id|/accountability-t05-pg-$($target.RUN)|$($target.IMAGE)" -or
        $labels.$label -cne $target.RUN -or $labels.PSObject.Properties['com.docker.compose.project'] -or
        $network -cne "$($target.NETWORK)|accountability-t05-net-$($target.RUN)|bridge|false" -or
        $networkLabels.$label -cne $target.RUN -or $networkLabels.PSObject.Properties['com.docker.compose.project'] -or
        $mode -cnotin @($target.NETWORK, "accountability-t05-net-$($target.RUN)") -or
        $tmpfs.Count -ne 1 -or $tmpfs[0].Type -cne 'tmpfs' -or $tmpfs[0].Target -cne $storage -or
        @($mounts | Where-Object { $_.Type -and ($_.Type -cne 'tmpfs' -or $_.Destination -cne $storage) }).Count -ne 0 -or
        @($bindings.PSObject.Properties).Count -ne 1 -or $bound.Count -ne 1 -or $bound[0].HostIp -cne '127.0.0.1') {
        throw 'Container, rede, tmpfs ou publicacao T05 divergente.'
    }
    if ($BeforeStart) {
        if ($bound[0].HostPort -cnotin @('', '0')) { throw 'T05 requer porta dinamica.' }
        return
    }
    if ($target.PORT -cnotmatch '^[1-9][0-9]{0,4}$' -or [int]$target.PORT -eq 5432 -or
        [int]$target.PORT -gt 65535 -or $bound[0].HostPort -cnotin @('', '0', $target.PORT)) {
        throw 'Porta configurada divergente.'
    }
    $attached = Invoke-Docker inspect --type container --format '{{json .NetworkSettings.Networks}}' $id | ConvertFrom-Json
    $networks = @($attached.PSObject.Properties)
    $members = Invoke-Docker network inspect --format '{{json .Containers}}' $target.NETWORK | ConvertFrom-Json
    $memberIds = @($members.PSObject.Properties.Name)
    if ($networks.Count -ne 1 -or $networks[0].Value.NetworkID -cne $target.NETWORK -or
        $memberIds.Count -ne 1 -or $memberIds[0] -cne $id) {
        throw 'Rede ativa divergente.'
    }
    $state = Invoke-Docker inspect --type container --format '{{.State.Status}}|{{.State.Running}}|{{.State.Paused}}' $id
    $ports = Invoke-Docker inspect --type container --format '{{json .NetworkSettings.Ports}}' $id | ConvertFrom-Json
    $publishedBindings = @($ports.'5432/tcp')
    $portCount = @($ports.PSObject.Properties).Count
    $expectedBinding = $portCount -eq 1 -and $publishedBindings.Count -eq 1 -and
        $publishedBindings[0].HostIp -ceq '127.0.0.1' -and $publishedBindings[0].HostPort -ceq $target.PORT
    if ($AllowPaused -and $state -ceq 'paused|true|true') {
        if ($portCount -ne 0 -and -not $expectedBinding) { throw 'Publicacao divergente durante a pausa.' }
        return 'paused'
    }
    if ($state -cne 'running|true|false' -or -not $expectedBinding) { throw 'Publicacao ativa divergente.' }
    $published = Invoke-Docker port $id 5432/tcp
    if ($published -cne "127.0.0.1:$($target.PORT)") { throw 'Porta publicada divergente.' }
}

function New-Hex([int]$bytes) {
    $buffer = [byte[]]::new($bytes)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($buffer) } finally { $random.Dispose() }
    [BitConverter]::ToString($buffer).Replace('-', '').ToLowerInvariant()
}

function New-Target($image, $targets) {
    $run = [guid]::NewGuid().ToString('N')
    $target = @{
        RUN=$run; IMAGE=$image; DATABASE="acct_t05_$run"; MARKER=(New-Hex 16)
        APPLICATION_PASSWORD=(New-Hex 32); MIGRATOR_PASSWORD=(New-Hex 32)
    }
    $targets.Add($target)
    $target.NETWORK = Invoke-Docker network create --driver bridge --label "$label=$run" "accountability-t05-net-$run"
    $savedPassword = $env:POSTGRES_PASSWORD
    $savedDatabase = $env:POSTGRES_DB
    try {
        $env:POSTGRES_PASSWORD = New-Hex 32
        $env:POSTGRES_DB = $target.DATABASE
        $createArgs = @('create','--name',"accountability-t05-pg-$run",'--label',"$label=$run",
            '--network',$target.NETWORK,'--mount',"type=tmpfs,destination=$storage",
            '--publish','127.0.0.1::5432','--env','POSTGRES_PASSWORD','--env','POSTGRES_DB',$image)
        $target.CONTAINER = Invoke-Docker @createArgs
    } finally {
        $env:POSTGRES_PASSWORD = $savedPassword
        $env:POSTGRES_DB = $savedDatabase
    }
    Assert-Container $target -BeforeStart
    $null = Invoke-Docker start $target.CONTAINER
    $mapping = Invoke-Docker port $target.CONTAINER 5432/tcp
    if ($mapping -cnotmatch '^127\.0\.0\.1:([1-9][0-9]{0,4})$') { throw 'Porta loopback T05 indisponivel.' }
    $target.PORT = $Matches[1]
    Assert-Container $target
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        & $docker --context $context exec $target.CONTAINER pg_isready -q -h 127.0.0.1 -d $target.DATABASE 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL T05 nao inicializou.' }

    $bootstrap = @(
        "\set database '$($target.DATABASE)'"
        "\set run_marker '$($target.MARKER)'"
        "\set application_password '$($target.APPLICATION_PASSWORD)'"
        "\set migrator_password '$($target.MIGRATOR_PASSWORD)'"
        (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'bootstrap.sql'))
    ) -join [Environment]::NewLine
    Assert-Container $target
    $bootstrap | & $docker --context $context exec -i $target.CONTAINER psql -X -q -v ON_ERROR_STOP=1 -1 -f - -U postgres -d $target.DATABASE 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Bootstrap sintético falhou.' }
    Write-Host "T05 criou container $($target.CONTAINER), rede $($target.NETWORK), porta $($target.PORT), banco $($target.DATABASE)."
    $target
}

function Remove-Target($target) {
    Assert-Context
    if ($target.ContainsKey('CONTAINER')) {
        $id = Invoke-Docker inspect --type container --format '{{.Id}}' $target.CONTAINER
        $labels = Invoke-Docker inspect --type container --format '{{json .Config.Labels}}' $target.CONTAINER | ConvertFrom-Json
        if ($id -cne $target.CONTAINER -or $labels.$label -cne $target.RUN) { throw 'Identidade de limpeza do container divergente.' }
        $null = Invoke-Docker rm --force $target.CONTAINER
        Write-Host "T05 removeu container $($target.CONTAINER)."
    }
    if ($target.ContainsKey('NETWORK')) {
        $id = Invoke-Docker network inspect --format '{{.ID}}' $target.NETWORK
        $labels = Invoke-Docker network inspect --format '{{json .Labels}}' $target.NETWORK | ConvertFrom-Json
        if ($id -cne $target.NETWORK -or $labels.$label -cne $target.RUN) { throw 'Identidade de limpeza da rede divergente.' }
        $null = Invoke-Docker network rm $target.NETWORK
        Write-Host "T05 removeu rede $($target.NETWORK)."
    }
}

Assert-Context
if ($Action -ne 'Verify') {
    $target = @{RUN=$env:T05_RUN; CONTAINER=$env:T05_CONTAINER; NETWORK=$env:T05_NETWORK; IMAGE=$env:T05_IMAGE; PORT=$env:T05_PORT}
    $state = Assert-Container $target -AllowPaused
    if ($Action -eq 'Attest' -and $state -ceq 'paused') { exit 42 }
    if ($Action -eq 'Pause') {
        if ($state -ceq 'paused') { throw 'O container ja esta pausado.' }
        $null = Invoke-Docker pause $target.CONTAINER
        if ((Assert-Container $target -AllowPaused) -cne 'paused') { throw 'A pausa T05 nao foi comprovada.' }
    }
    if ($Action -eq 'Resume') {
        if ($state -cne 'paused') { throw 'O container nao esta pausado.' }
        $null = Invoke-Docker unpause $target.CONTAINER
        Assert-Container $target
    }
    exit 0
}

function Save-And-SetProcessVariable($name, $value, $saved) {
    if (-not $saved.ContainsKey($name)) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$targets = [System.Collections.Generic.List[hashtable]]::new()
$oldEnvironment = @{}
$locationChanged = $false
try {
    $environmentNames = @([Environment]::GetEnvironmentVariables('Process').Keys)
    foreach ($name in $environmentNames) {
        if ($name -match '^(SPRING|SERVER|MANAGEMENT)[_.]' -or
            $name -match '^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_OPTS|MAVEN_ARGS)$') {
            Save-And-SetProcessVariable $name $null $oldEnvironment
        }
    }
    $null = Invoke-Docker pull docker.io/library/postgres:17-alpine
    $resolved = Invoke-Docker image inspect --format '{{json .RepoDigests}}' docker.io/library/postgres:17-alpine | ConvertFrom-Json
    $digests = @($resolved | Where-Object { $_ -cmatch '^(docker\.io/library/|library/)?postgres@sha256:[0-9a-f]{64}$' })
    if ($digests.Count -ne 1) { throw 'Digest da imagem oficial PostgreSQL ambiguo ou ausente.' }
    $image = 'docker.io/library/postgres@' + ($digests[0] -split '@', 2)[1]
    $null = Invoke-Docker image inspect --format '{{.Id}}' $image
    Write-Host "T05 imagem oficial $image."
    foreach ($prefix in @('T05_', 'T05_DRIFT_', 'T05_ROLLBACK_')) {
        $target = New-Target $image $targets
        foreach ($key in $target.Keys) {
            $name = $prefix + $key
            Save-And-SetProcessVariable $name $target[$key] $oldEnvironment
        }
    }
    Push-Location (Join-Path $PSScriptRoot '..\..')
    $locationChanged = $true
    & .\mvnw.cmd -B -ntp -Pt05-postgres-it verify
    if ($LASTEXITCODE -ne 0) { throw 'Testes de integracao T05 falharam.' }
} finally {
    if ($locationChanged) { Pop-Location }
    foreach ($key in $oldEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $oldEnvironment[$key], 'Process')
    }
    $cleanupFailed = $false
    foreach ($target in $targets) {
        try { Remove-Target $target } catch {
            $cleanupFailed = $true
            Write-Warning 'Limpeza T05 incompleta. Confira a identidade dos IDs antes de tentar novamente.'
        }
    }
    if ($cleanupFailed) { throw 'Limpeza T05 incompleta.' }
}
