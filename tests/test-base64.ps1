param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$root = Split-Path -Parent $PSScriptRoot
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-base64-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    $sources = @(Get-ChildItem (Join-Path $root 'src/main/java/org/json') -Filter '*.java' | ForEach-Object FullName)
    $sources += Join-Path $root 'src/main/java/lk/vexview/net/Base64ImagePackets.java'
    $sources += Join-Path $PSScriptRoot 'java/lk/vexview/net/Base64ImagePacketsTest.java'
    & $javac -source 8 -target 8 -encoding UTF-8 -d $scratch $sources
    if ($LASTEXITCODE -ne 0) { throw 'Base64 test compilation failed' }
    & $java '-Djava.awt.headless=true' -Xverify:all -cp $scratch lk.vexview.net.Base64ImagePacketsTest
    if ($LASTEXITCODE -ne 0) { throw 'Base64 test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
