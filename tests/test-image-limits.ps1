param([string]$JavaHome = $env:JAVA_HOME, [string]$JavaRuntime = '', [string]$SourceRoot = (Join-Path $PSScriptRoot '../src/main/java'))
$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaRuntime) { $JavaRuntime } elseif ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-image-limits-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $scratch | Out-Null
    $sources = @(
        (Join-Path $SourceRoot 'lk/vexview/engine/ImageReadLimits.java'),
        (Join-Path $SourceRoot 'lk/vexview/gui/core/GifDecoder.java'),
        (Join-Path $SourceRoot 'lk/vexview/gui/core/GifDecoder$GifFrame.java'),
        (Join-Path $PSScriptRoot 'java/lk/vexview/gui/core/ImageReadLimitsTest.java')
    )
    & $javac -source 8 -target 8 -encoding UTF-8 -d $scratch $sources
    if ($LASTEXITCODE -ne 0) { throw 'Image limits test compilation failed' }
    & $java '-Djava.awt.headless=true' -Xverify:all -cp $scratch lk.vexview.gui.core.ImageReadLimitsTest
    if ($LASTEXITCODE -ne 0) { throw 'Image limits test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
