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
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-image-download-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 -cp ($cp -join ';') -d $scratch (Join-Path $PSScriptRoot 'java/VerifyImageDownload.java')
    if ($LASTEXITCODE -ne 0) { throw 'Image download test compilation failed' }
    & $java '-Djava.awt.headless=true' -Xverify:all -cp ((@($scratch) + $cp) -join ';') VerifyImageDownload
    if ($LASTEXITCODE -ne 0) { throw 'Image download test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
