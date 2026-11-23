param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$root = Split-Path -Parent $PSScriptRoot
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-texture-cache-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 -d $scratch (Join-Path $root 'src/main/java/lk/vexview/engine/ConnectionTextureCache.java') (Join-Path $PSScriptRoot 'ConnectionTextureCacheTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Texture cache test compilation failed' }
    & $java -Xverify:all -cp $scratch ConnectionTextureCacheTest
    if ($LASTEXITCODE -ne 0) { throw 'Texture cache test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
