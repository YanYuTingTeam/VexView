param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'build/classes'
$cpFile = Join-Path $root 'build-tools/cp.txt'
if (-not (Test-Path $classes) -or -not (Test-Path $cpFile)) {
    throw 'Run build-local.ps1 first to create build/classes and build-tools/cp.txt.'
}
$cp = @($classes) + @(Get-Content -LiteralPath $cpFile)
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-hud-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 -cp ($cp -join ';') -d $scratch (Join-Path $PSScriptRoot 'java/VerifyHudLayers.java')
    if ($LASTEXITCODE -ne 0) { throw 'HUD test compilation failed' }
    & $java -Xverify:all -cp ((@($scratch) + $cp) -join ';') VerifyHudLayers
    if ($LASTEXITCODE -ne 0) { throw 'HUD test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
