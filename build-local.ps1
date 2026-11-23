# Offline build for the VexView 2.7.3 port to Minecraft 1.8.9 / Forge 11.15.1.
#
#   javac (JDK 8)  ->  reobf (MCP member names -> SRG member names)  ->  jar
#
# The result is a drop-in Forge 1.8.9 client mod jar.
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Version = '2.7.3',
    [string]$OutJar = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
$tools = Join-Path $root 'build-tools'

function Resolve-Jdk([string]$p) {
    if (-not $p) { return $null }
    if (Test-Path (Join-Path $p 'bin\javac.exe')) { return $p }
    return $null
}
$JavaHome = Resolve-Jdk $JavaHome
if (-not $JavaHome) { throw 'JDK 8 not found: set JAVA_HOME or pass -JavaHome <path-to-jdk8>' }
$javac  = Join-Path $JavaHome 'bin\javac.exe'
$java   = Join-Path $JavaHome 'bin\java.exe'
$jarEx  = Join-Path $JavaHome 'bin\jar.exe'
Write-Host "JDK: $JavaHome"

$sdk = Join-Path $tools 'minecraft-1.8.9-forge-mcp.jar'
$map = Join-Path $tools 'mcp2srg-1.8.9.map'
$cpTxt = Join-Path $tools 'cp.txt'
if (-not (Test-Path $sdk) -or -not (Test-Path $map) -or -not (Test-Path $cpTxt)) {
    Write-Host '== toolchain missing, running build-tools\prepare-sdk.ps1'
    & (Join-Path $tools 'prepare-sdk.ps1') -JavaHome $JavaHome
}
$cp = ((Get-Content $cpTxt) -join ';')

$toolCp = ((Get-ChildItem (Join-Path $tools 'lib') -Filter *.jar | ForEach-Object { $_.FullName }) + (Join-Path $tools 'out')) -join ';'

$build   = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$stage   = Join-Path $build 'stage'
Remove-Item $classes, $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host '== compile'
$sourceList = Join-Path $build 'sources.txt'
Push-Location -LiteralPath $root
try {
    Get-ChildItem -LiteralPath 'src\main\java' -Recurse -File -Filter '*.java' |
        ForEach-Object { '"' + (Resolve-Path -LiteralPath $_.FullName -Relative).Replace('\', '/') + '"' } |
        Set-Content -LiteralPath $sourceList -Encoding ASCII
    & $javac -source 8 -target 8 -encoding UTF-8 -nowarn -Xmaxerrs 20000 -d $classes -cp $cp "@$sourceList"
    if ($LASTEXITCODE -ne 0) { throw 'compilation failed' }
} finally {
    Pop-Location
}

Write-Host '== reobf (MCP -> SRG)'
$srgJar = Join-Path $build 'classes-srg.jar'
& $java -Xmx3G -cp $toolCp Reobf $classes $sdk $map $srgJar
if ($LASTEXITCODE -ne 0) { throw 'reobf failed' }

Write-Host '== package'
Push-Location $stage
& $jarEx xf $srgJar
Pop-Location
Copy-Item (Join-Path $root 'src\main\resources\*') $stage -Recurse -Force

if (-not $OutJar) { $OutJar = Join-Path $build "libs\vexview-$Version-1.8.9.jar" }
New-Item -ItemType Directory -Force -Path (Split-Path $OutJar) | Out-Null
if (Test-Path $OutJar) { Remove-Item $OutJar -Force }
Push-Location $stage
& $jarEx cf $OutJar .
Pop-Location
if ($LASTEXITCODE -ne 0) { throw 'jar packaging failed' }

Write-Host "OK -> $OutJar"
