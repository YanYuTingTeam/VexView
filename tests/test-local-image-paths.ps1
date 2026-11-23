param([string]$JavaHome = $env:JAVA_HOME, [string]$JavaRuntime = '')
$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaRuntime) { $JavaRuntime } elseif ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$root = Split-Path -Parent $PSScriptRoot
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('vex-local-paths-test-' + [guid]::NewGuid().ToString('N'))
try {
    $classes = Join-Path $scratch 'classes'
    $data = Join-Path $scratch 'data'
    New-Item -ItemType Directory -Path $classes, $data | Out-Null
    if ($env:OS -eq 'Windows_NT') {
        $imageRoot = Join-Path $data 'assets'
        $outside = Join-Path $data 'assets-sibling'
        New-Item -ItemType Directory -Path $imageRoot, $outside | Out-Null
        try {
            New-Item -ItemType Junction -Path (Join-Path $imageRoot 'outside-link') -Target $outside | Out-Null
        } catch {
            Write-Host 'Windows junction unavailable; Java will try a symbolic link.'
        }
    }
    & $javac -source 8 -target 8 -encoding UTF-8 -d $classes (Join-Path $root 'src/main/java/lk/vexview/engine/LocalImagePaths.java') (Join-Path $PSScriptRoot 'java/lk/vexview/engine/LocalImagePathsTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Local image path test compilation failed' }
    & $java -Xverify:all -cp $classes lk.vexview.engine.LocalImagePathsTest $data
    if ($LASTEXITCODE -ne 0) { throw 'Local image path test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
}
