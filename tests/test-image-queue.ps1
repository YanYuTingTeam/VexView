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
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-image-queue-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 -cp ($cp -join ';') -d $scratch `
        (Join-Path $PSScriptRoot 'stubs/net/minecraft/client/resources/I18n.java') `
        (Join-Path $PSScriptRoot 'java/lk/vexview/ImageQueueTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Image queue test compilation failed' }
    & $java -Xverify:all -cp ((@($scratch) + $cp) -join ';') lk.vexview.ImageQueueTest
    if ($LASTEXITCODE -ne 0) { throw 'Image queue test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
