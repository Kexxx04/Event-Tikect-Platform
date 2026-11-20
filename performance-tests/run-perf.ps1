<#
.SYNOPSIS
    Ejecuta un escenario de JMeter contra la API con los repositorios en memoria, en Docker y con recursos limitados.

.DESCRIPTION
    1. Construye event-platform/build/libs/event-platform-inmemory.jar (tarea inMemoryBootJar).
    2. Genera ADMIN_PASSWORD y JWT_SECRET aleatorios para esta ejecucion (no se guardan en ningun archivo).
    3. Levanta la API de compose.perf.yaml (2 CPU, 1 GiB) y espera a que /actuator/health responda UP.
    4. Ejecuta JMeter en modo non-GUI en la red interna y muestrea docker stats cada 5 s.
    5. Deja en performance-tests/results/<escenario>-<fecha>/ el .jtl, el dashboard HTML, docker-stats.csv,
       jmeter.log y resumen.md con los criterios de aceptacion.
    6. Siempre baja los contenedores, aunque algo falle.

    Codigo de salida: 0 si la prueba se ejecuto (los criterios incumplidos solo dejan advertencias);
    1 si hubo un error de ejecucion o si el escenario aforo encontro sobrecupo.

.PARAMETER Scenario
    normal: 50 usuarios, ramp-up 30 s, 5 min. pico: 200 usuarios, ramp-up 60 s, 5 min.
    aforo: 1000 inscripciones simultaneas a un evento de 100 cupos.

.PARAMETER DurationSeconds
    Cambia la duracion de normal o pico (por ejemplo 60 para una prueba rapida del script).

.PARAMETER SkipBuild
    No ejecuta Gradle ni construye las imagenes: usa el jar y las imagenes que ya existen (no necesita red).

.PARAMETER BuildOnly
    Solo construye el jar y las imagenes (el paso que descarga dependencias) y termina.

.EXAMPLE
    .\run-perf.ps1 -Scenario normal

.EXAMPLE
    .\run-perf.ps1 -BuildOnly; .\run-perf.ps1 -Scenario aforo -SkipBuild
#>
[CmdletBinding(DefaultParameterSetName = 'Run')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Run')]
    [ValidateSet('normal', 'pico', 'aforo')]
    [string] $Scenario,

    [Parameter(ParameterSetName = 'Run')]
    [ValidateRange(0, 86400)]
    [int] $DurationSeconds = 0,

    [Parameter(ParameterSetName = 'Run')]
    [switch] $SkipBuild,

    [Parameter(Mandatory = $true, ParameterSetName = 'Build')]
    [switch] $BuildOnly
)

$ErrorActionPreference = 'Stop'

$perfDir = $PSScriptRoot
$apiDir = Join-Path (Split-Path -Parent $perfDir) 'event-platform'
$composeFile = Join-Path $perfDir 'compose.perf.yaml'
$project = 'event-platform-perf'
$apiPort = if ($env:PERF_API_PORT) { $env:PERF_API_PORT } else { '18080' }
$inPipeline = [bool] $env:TF_BUILD
$invariant = [Globalization.CultureInfo]::InvariantCulture

$scenarios = @{
    normal = @{ Plan = 'carga.jmx'; Properties = [ordered]@{ users = 50; rampup = 30; duration = 300 } }
    pico   = @{ Plan = 'carga.jmx'; Properties = [ordered]@{ users = 200; rampup = 60; duration = 300 } }
    aforo  = @{ Plan = 'aforo-concurrente.jmx'; Properties = [ordered]@{ users = 1000; capacity = 100; rampup = 10 } }
}

# Criterios de aceptacion.
$maxErrorPercent = 1.0
$maxP95QueryMs = 500
$maxP95WriteMs = 1000

function Write-Step([string] $message) {
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Write-CriterionWarning([string] $message) {
    if ($inPipeline) { Write-Host "##vso[task.logissue type=warning]$message" } else { Write-Warning $message }
}

function New-Secret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

# docker y Gradle escriben su progreso en stderr; en Windows PowerShell eso no debe detener el script,
# asi que aqui decide solo el codigo de salida. Devuelve ese codigo.
function Invoke-Native([string] $command, [string[]] $Arguments) {
    $ErrorActionPreference = 'Continue'
    & $command @Arguments 2>&1 | ForEach-Object { Write-Host $_ }
    return $LASTEXITCODE
}

function Invoke-Compose([string[]] $Arguments) {
    $code = Invoke-Native 'docker' (@('compose', '-f', $composeFile, '-p', $project) + $Arguments)
    if ($code -ne 0) { throw "docker compose $($Arguments -join ' ') fallo con codigo $code" }
}

function Wait-ApiHealth([string] $url, [int] $timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri $url -TimeoutSec 5
            if ($health.status -eq 'UP') { return }
        } catch {
            # Todavia arrancando.
        }
        Start-Sleep -Seconds 2
    }
    throw "La API no respondio UP en $url"
}

# Muestrea CPU y memoria de los contenedores del proyecto cada 5 s en un proceso aparte.
function Start-StatsSampler([string] $file) {
    return Start-Job -ArgumentList $file, $project -ScriptBlock {
        param([string] $file, [string] $project)
        $invariant = [Globalization.CultureInfo]::InvariantCulture
        function ConvertTo-MiB([string] $value) {
            if ($value -notmatch '^\s*([\d.]+)\s*([A-Za-z]+)\s*$') { return [double]::NaN }
            $number = [double]::Parse($Matches[1], $invariant)
            switch -CaseSensitive ($Matches[2]) {
                'B' { return $number / 1MB }
                'KiB' { return $number / 1KB }
                'kB' { return $number / 1KB }
                'MiB' { return $number }
                'MB' { return $number }
                'GiB' { return $number * 1KB }
                'GB' { return $number * 1KB }
                default { return [double]::NaN }
            }
        }
        Set-Content -Path $file -Encoding ASCII -Value 'timestamp,container,cpu_percent,mem_used_mib,mem_limit_mib,mem_percent'
        while ($true) {
            $started = Get-Date
            $ids = @(docker ps -q --filter "label=com.docker.compose.project=$project")
            if ($ids.Count -gt 0) {
                $lines = @(docker stats --no-stream --format '{{.Name}};{{.CPUPerc}};{{.MemUsage}};{{.MemPerc}}' @ids)
                foreach ($line in $lines) {
                    $parts = $line -split ';'
                    if ($parts.Count -lt 4) { continue }
                    $memory = $parts[2] -split '/'
                    $row = '{0},{1},{2},{3},{4},{5}' -f $started.ToString('s'), $parts[0], $parts[1].TrimEnd('%'),
                        (ConvertTo-MiB $memory[0]).ToString('0.0', $invariant),
                        (ConvertTo-MiB $memory[1]).ToString('0.0', $invariant), $parts[3].TrimEnd('%')
                    Add-Content -Path $file -Encoding ASCII -Value $row
                }
            }
            $waitMs = 5000 - [int]((Get-Date) - $started).TotalMilliseconds
            if ($waitMs -gt 0) { Start-Sleep -Milliseconds $waitMs }
        }
    }
}

function Format-Number([double] $value, [string] $format = '0') {
    return $value.ToString($format, $invariant)
}

function Get-Criterion([string] $label) {
    if ($label -eq 'Total') { return @{ Kind = 'Total'; MaxP95 = $null } }
    if ($label -match '\((setUp|tearDown)\)$') { return @{ Kind = 'Preparacion'; MaxP95 = $null } }
    if ($label -like 'GET *') { return @{ Kind = 'Consulta'; MaxP95 = $maxP95QueryMs } }
    if ($label -eq 'POST /api/users' -or $label -like 'POST /api/events/*/registrations') {
        return @{ Kind = 'Creacion'; MaxP95 = $maxP95WriteMs }
    }
    return @{ Kind = 'Otra'; MaxP95 = $null }
}

# Devuelve $true si el escenario aforo encontro sobrecupo (o no se pudo verificar).
function Write-Summary([string] $resultsDir, [Collections.IDictionary] $properties) {
    $statistics = Get-Content -Raw -Path (Join-Path $resultsDir 'dashboard\statistics.json') | ConvertFrom-Json
    $lines = New-Object System.Collections.Generic.List[string]
    $failed = New-Object System.Collections.Generic.List[string]
    $settings = ($properties.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '

    $lines.Add("# Resumen de rendimiento: escenario $Scenario")
    $lines.Add('')
    $lines.Add("- Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("- Parametros: $settings")
    $lines.Add('- API: event-platform-inmemory.jar (perfil inmemory, sin base de datos), limitada a 2 CPU y 1 GiB')
    $lines.Add("- Criterios: errores < $maxErrorPercent %; p95 < $maxP95QueryMs ms en consultas y < $maxP95WriteMs ms en creacion de usuario e inscripcion; 0 sobrecupos en aforo")
    $lines.Add('')
    $lines.Add('| Peticion | Tipo | Muestras | Promedio (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Throughput (req/s) | Errores (%) | Criterio | Resultado |')
    $lines.Add('| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |')

    $rows = @($statistics.PSObject.Properties | ForEach-Object { $_.Value } | Sort-Object { $_.transaction -eq 'Total' }, transaction)
    foreach ($row in $rows) {
        $criterion = Get-Criterion $row.transaction
        $meets = [double] $row.errorPct -lt $maxErrorPercent
        $text = "errores < $maxErrorPercent %"
        if ($null -ne $criterion.MaxP95) {
            $meets = $meets -and ([double] $row.pct2ResTime -lt $criterion.MaxP95)
            $text = "p95 < $($criterion.MaxP95) ms; $text"
        }
        $verdict = if ($meets) { 'CUMPLE' } else { 'NO CUMPLE' }
        if (-not $meets) { $failed.Add("$($row.transaction): $text") }
        $lines.Add(('| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} | {10} |' -f $row.transaction,
            $criterion.Kind, $row.sampleCount, (Format-Number $row.meanResTime), (Format-Number $row.pct1ResTime),
            (Format-Number $row.pct2ResTime), (Format-Number $row.pct3ResTime), (Format-Number $row.throughput '0.0'),
            (Format-Number $row.errorPct '0.00'), $text, $verdict))
    }

    $overbooking = $false
    if ($Scenario -eq 'aforo') {
        $lines.Add('')
        $lines.Add('## Aforo')
        $lines.Add('')
        $aforoFile = Join-Path $resultsDir 'aforo-resultado.json'
        if (-not (Test-Path $aforoFile)) {
            $overbooking = $true
            $failed.Add('aforo: no se genero aforo-resultado.json; no se pudo verificar el sobrecupo')
            $lines.Add('No se genero `aforo-resultado.json`: el tearDown no pudo consultar la disponibilidad. **NO CUMPLE**')
        } else {
            $aforo = Get-Content -Raw -Path $aforoFile | ConvertFrom-Json
            $capacity = [int] $aforo.capacity
            $expectedWaitlist = [int] $aforo.users - $capacity
            $registered = [int] $aforo.availability.registeredCount
            $waitlist = [int] $aforo.availability.waitlistCount
            $responses = $aforo.registrationResponses
            $confirmed = if ($responses.PSObject.Properties['CONFIRMED']) { [int] $responses.CONFIRMED } else { 0 }
            $waiting = if ($responses.PSObject.Properties['WAITING_LIST']) { [int] $responses.WAITING_LIST } else { 0 }
            $others = @($responses.PSObject.Properties | Where-Object { $_.Name -notin 'CONFIRMED', 'WAITING_LIST' } |
                ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', '
            $overbooking = ($registered -gt $capacity) -or ($confirmed -gt $capacity)
            $exact = ($registered -eq $capacity) -and ($waitlist -eq $expectedWaitlist) -and
                ($confirmed -eq $capacity) -and ($waiting -eq $expectedWaitlist)
            $lines.Add('| Dato | Esperado | Obtenido |')
            $lines.Add('| --- | ---: | ---: |')
            $lines.Add("| registeredCount (API) | $capacity | $registered |")
            $lines.Add("| waitlistCount (API) | $expectedWaitlist | $waitlist |")
            $lines.Add("| Respuestas CONFIRMED | $capacity | $confirmed |")
            $lines.Add("| Respuestas WAITING_LIST | $expectedWaitlist | $waiting |")
            if ($others) { $lines.Add("| Otras respuestas | 0 | $others |") }
            $lines.Add('')
            if ($overbooking) {
                $failed.Add("aforo: sobrecupo (registeredCount $registered, CONFIRMED $confirmed, capacidad $capacity)")
                $lines.Add("**Sobrecupo: NO CUMPLE.** Hay mas inscripciones confirmadas que cupos.")
            } elseif ($exact) {
                $lines.Add('**0 sobrecupos: CUMPLE.** Exactamente ' + $capacity + ' CONFIRMED y ' + $expectedWaitlist + ' WAITING_LIST.')
            } else {
                $failed.Add('aforo: sin sobrecupo, pero los conteos no son los esperados')
                $lines.Add('Sin sobrecupo, pero los conteos no son los esperados (revisa las respuestas con error): **NO CUMPLE**.')
            }
        }
    }

    $lines.Add('')
    $lines.Add('## Recursos de los contenedores (docker stats cada 5 s)')
    $lines.Add('')
    $statsFile = Join-Path $resultsDir 'docker-stats.csv'
    $samples = @(if (Test-Path $statsFile) { Import-Csv -Path $statsFile })
    if ($samples.Count -eq 0) {
        $lines.Add('Sin muestras.')
    } else {
        $lines.Add('| Contenedor | Muestras | CPU maxima (%) | CPU promedio (%) | Memoria maxima (MiB) | Limite (MiB) |')
        $lines.Add('| --- | ---: | ---: | ---: | ---: | ---: |')
        foreach ($group in ($samples | Group-Object container)) {
            $cpu = @($group.Group | ForEach-Object { [double]::Parse($_.cpu_percent, $invariant) })
            $memory = @($group.Group | ForEach-Object { [double]::Parse($_.mem_used_mib, $invariant) })
            $cpuStats = $cpu | Measure-Object -Maximum -Average
            $lines.Add(('| {0} | {1} | {2} | {3} | {4} | {5} |' -f $group.Name, $group.Count,
                (Format-Number $cpuStats.Maximum '0.0'), (Format-Number $cpuStats.Average '0.0'),
                (Format-Number ($memory | Measure-Object -Maximum).Maximum '0.0'), $group.Group[0].mem_limit_mib))
        }
        $lines.Add('')
        $lines.Add('La CPU de docker stats es relativa a un nucleo: con el limite de 2 CPU, el maximo posible es 200 %.')
    }

    $lines.Add('')
    if ($failed.Count -eq 0) {
        $lines.Add('**Resultado global: CUMPLE todos los criterios.**')
    } else {
        $lines.Add('**Resultado global: NO CUMPLE** ' + $failed.Count + ' criterio(s):')
        $lines.Add('')
        foreach ($item in $failed) { $lines.Add("- $item") }
    }

    $summaryFile = Join-Path $resultsDir 'resumen.md'
    [IO.File]::WriteAllLines($summaryFile, $lines, (New-Object Text.UTF8Encoding $false))
    Write-Host ''
    $lines | ForEach-Object { Write-Host $_ }
    foreach ($item in $failed) { Write-CriterionWarning "Rendimiento ($Scenario) NO CUMPLE: $item" }
    return $overbooking
}

# ---------------------------------------------------------------------------------------------------- inicio

if ((Invoke-Native 'docker' @('version', '--format', 'Docker {{.Server.Version}}')) -ne 0) {
    throw 'Docker no esta disponible: abre Docker Desktop (contenedores Linux).'
}
$osType = & docker info --format '{{.OSType}}'
if ("$osType".Trim() -ne 'linux') { throw 'Docker debe ejecutar contenedores Linux.' }

# Secretos solo de esta ejecucion: viven en variables de entorno del proceso y mueren con los contenedores.
# compose.perf.yaml los exige incluso para construir las imagenes.
$env:ADMIN_USERNAME = 'perf_admin'
$env:ADMIN_PASSWORD = New-Secret
$env:JWT_SECRET = New-Secret
if ($inPipeline) {
    Write-Host "##vso[task.setsecret]$($env:ADMIN_PASSWORD)"
    Write-Host "##vso[task.setsecret]$($env:JWT_SECRET)"
}

if (-not $SkipBuild) {
    Write-Step 'Construyendo build/libs/event-platform-inmemory.jar (gradlew inMemoryBootJar)'
    $gradleArgs = @('-p', $apiDir, 'inMemoryBootJar', '--console=plain')
    if ($inPipeline) { $gradleArgs += '--no-daemon' }
    $code = Invoke-Native (Join-Path $apiDir 'gradlew.bat') $gradleArgs
    if ($code -ne 0) { throw "gradlew inMemoryBootJar fallo con codigo $code" }

    Write-Step 'Construyendo las imagenes (API en memoria y JMeter 5.6.3)'
    Invoke-Compose @('--profile', 'jmeter', 'build')
}
$jar = Join-Path $apiDir 'build\libs\event-platform-inmemory.jar'
if (-not (Test-Path $jar)) { throw "No existe $jar; ejecuta sin -SkipBuild." }

if ($BuildOnly) {
    Remove-Item Env:ADMIN_USERNAME, Env:ADMIN_PASSWORD, Env:JWT_SECRET -ErrorAction SilentlyContinue
    Write-Host 'Jar e imagenes listos.' -ForegroundColor Green
    exit 0
}

$definition = $scenarios[$Scenario]
$properties = [ordered]@{}
foreach ($entry in $definition.Properties.GetEnumerator()) { $properties[$entry.Key] = $entry.Value }
if ($DurationSeconds -gt 0 -and $properties.Contains('duration')) { $properties['duration'] = $DurationSeconds }

$runName = '{0}-{1}' -f $Scenario, (Get-Date -Format 'yyyyMMdd-HHmmss')
$resultsDir = Join-Path $perfDir "results\$runName"
$containerResults = "/results/$runName"
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null

$statsJob = $null
$exitCode = 1
try {
    Write-Step 'Levantando la API (2 CPU, 1 GiB) y esperando /actuator/health'
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '180', 'api')
    Wait-ApiHealth "http://127.0.0.1:$apiPort/actuator/health" 60

    $statsJob = Start-StatsSampler (Join-Path $resultsDir 'docker-stats.csv')

    Write-Step "Ejecutando JMeter: $($definition.Plan) ($(($properties.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '))"
    $jmeterArgs = @('--profile', 'jmeter', 'run', '--rm', '--no-deps', 'jmeter',
        '-n', '-t', "/plans/$($definition.Plan)",
        '-l', "$containerResults/resultados.jtl",
        '-j', "$containerResults/jmeter.log",
        '-e', '-o', "$containerResults/dashboard",
        '-Jhost=api', '-Jport=8080', "-JresultsDir=$containerResults")
    foreach ($entry in $properties.GetEnumerator()) { $jmeterArgs += "-J$($entry.Key)=$($entry.Value)" }
    $jmeterExit = Invoke-Native 'docker' (@('compose', '-f', $composeFile, '-p', $project) + $jmeterArgs)

    Stop-Job -Job $statsJob
    Remove-Job -Job $statsJob -Force
    $statsJob = $null

    if ($jmeterExit -ne 0) { throw "JMeter termino con codigo $jmeterExit; revisa $resultsDir\jmeter.log" }
    if (-not (Test-Path (Join-Path $resultsDir 'dashboard\statistics.json'))) {
        throw "JMeter no genero el dashboard; revisa $resultsDir\jmeter.log"
    }

    $overbooking = Write-Summary $resultsDir $properties
    if ($overbooking) {
        if ($inPipeline) { Write-Host '##vso[task.logissue type=error]Escenario aforo: sobrecupo o aforo sin verificar.' }
        $exitCode = 1
    } else {
        $exitCode = 0
    }
    Write-Host ''
    Write-Host "Resultados: $resultsDir" -ForegroundColor Green
} finally {
    if ($null -ne $statsJob) {
        Stop-Job -Job $statsJob
        Remove-Job -Job $statsJob -Force
    }
    Write-Step 'Bajando los contenedores'
    $null = Invoke-Native 'docker' @('compose', '-f', $composeFile, '-p', $project, '--profile', 'jmeter',
        'down', '--volumes', '--remove-orphans')
    Remove-Item Env:ADMIN_USERNAME, Env:ADMIN_PASSWORD, Env:JWT_SECRET -ErrorAction SilentlyContinue
}
exit $exitCode
