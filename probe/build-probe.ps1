# Builds the verification probe mod: javac -> reobf (MC members to SRG) -> jar.
param(
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe') -PathType Leaf)) {
    throw 'JDK 8 not found: set JAVA_HOME or pass -JavaHome <path-to-jdk8>'
}
$tools = Join-Path $root 'build-tools'
$sdk = Join-Path $tools 'minecraft-1.8.9-forge-mcp.jar'
$cpTxt = Join-Path $tools 'cp.txt'
if (-not (Test-Path $sdk) -or -not (Test-Path $cpTxt)) {
    & (Join-Path $tools 'prepare-sdk.ps1') -JavaHome $JavaHome
}
$cp = ((Get-Content $cpTxt) -join ';')
$toolCp = ((Get-ChildItem (Join-Path $tools 'lib') -Filter *.jar | ForEach-Object { $_.FullName }) + (Join-Path $tools 'out')) -join ';'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
$jarEx = Join-Path $JavaHome 'bin\jar.exe'

$classes = Join-Path $root 'probe\classes'
$stage = Join-Path $root 'probe\stage'
Remove-Item $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes | Out-Null

& $javac -source 8 -target 8 -encoding UTF-8 -nowarn -d $classes -cp $cp (Join-Path $root 'probe\src\vexprobe\ProbeMod.java')
if ($LASTEXITCODE -ne 0) { throw 'probe compile failed' }

& $java -Xmx2G -cp $toolCp Reobf $classes $sdk (Join-Path $tools 'mcp2srg-1.8.9.map') (Join-Path $root 'probe\probe-srg.jar')
if ($LASTEXITCODE -ne 0) { throw 'probe reobf failed' }

Push-Location $stage
& $jarEx xf (Join-Path $root 'probe\probe-srg.jar') 'vexprobe'
Pop-Location
Remove-Item (Join-Path $root 'probe\vexprobe.jar') -Force -ErrorAction SilentlyContinue
& $jarEx cf (Join-Path $root 'probe\vexprobe.jar') -C $stage .
if ($LASTEXITCODE -ne 0) { throw 'probe packaging failed' }
$mods = Join-Path $root 'runtest\mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null
Copy-Item (Join-Path $root 'probe\vexprobe.jar') $mods -Force
Write-Host "OK -> probe\vexprobe.jar"
