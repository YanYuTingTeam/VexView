# Launches a real Minecraft 1.8.9 + Forge 11.15.1.1902 client with the ported VexView jar in mods/.
# Used as the end-to-end verification: FML must discover, load, preInit and init the mod.
param(
    [string]$JavaHome = 'D:\deobf\_tools\jdk8\zulu8.78.0.19-ca-jdk8.0.412-win_x64',
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$mc = Join-Path $env:APPDATA '.minecraft'
$run = 'D:\src\vexview1.8.9\runtest'
$modJar = 'D:\src\vexview1.8.9\build\libs\vexview-2.7.3-1.8.9.jar'

New-Item -ItemType Directory -Force -Path (Join-Path $run 'mods'), (Join-Path $run 'logs') | Out-Null
Copy-Item $modJar (Join-Path $run 'mods') -Force

$libs = Join-Path $mc 'libraries'
$json = Get-Content (Join-Path $mc 'versions\1.8.9-Forge_11.15.1.1902-OptiFine_M5\1.8.9-Forge_11.15.1.1902-OptiFine_M5.json') -Raw | ConvertFrom-Json
$cp = New-Object System.Collections.Generic.List[string]
foreach ($l in $json.libraries) {
    if ($l.name -match 'natives') { continue }
    $p = $l.name.Split(':')
    $rel = ($p[0] -replace '\.', '\') + "\$($p[1])\$($p[2])\$($p[1])-$($p[2]).jar"
    $full = Join-Path $libs $rel
    if (Test-Path $full) { $cp.Add($full) }
}
$cp.Add((Join-Path $mc 'versions\1.8.9\1.8.9.jar'))
$forge = Join-Path $libs 'net\minecraftforge\forge\1.8.9-11.15.1.1902-1.8.9\forge-1.8.9-11.15.1.1902-1.8.9.jar'
$cp.Add($forge)

$natives = Join-Path $mc 'versions\1.8.9-Forge_11.15.1.1902-OptiFine_M5\1.8.9-Forge_11.15.1.1902-OptiFine_M5-natives'

$args = @(
    '-Xmx2G',
    "-Djava.library.path=$natives",
    "-Dorg.lwjgl.librarypath=$natives",
    '-Dfml.ignoreInvalidMinecraftCertificates=true',
    '-Dfml.ignorePatchDiscrepancies=true',
    '-cp', ($cp -join ';'),
    'net.minecraft.launchwrapper.Launch',
    '--username', 'DevTester',
    '--version', '1.8.9',
    '--gameDir', $run,
    '--assetsDir', (Join-Path $mc 'assets'),
    '--assetIndex', '1.8',
    '--uuid', '00000000000000000000000000000000',
    '--accessToken', '0',
    '--userProperties', '{}',
    '--userType', 'legacy',
    '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker'
)

Write-Host "launching ($($cp.Count) classpath entries) ..."
$proc = Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') -ArgumentList $args -WorkingDirectory $run -PassThru -RedirectStandardOutput (Join-Path $run 'logs\stdout.txt') -RedirectStandardError (Join-Path $run 'logs\stderr.txt')
Write-Host "pid=$($proc.Id)"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while (-not $proc.HasExited -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
if ($proc.HasExited) { Write-Host "exited with $($proc.ExitCode)" } else { Write-Host "still running after $TimeoutSeconds s (expected for a GUI client)" }
