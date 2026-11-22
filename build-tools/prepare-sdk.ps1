# 生成 1.8.9 的编译工具链（SDK / 映射 / reobf 表 / classpath）。
#
#   1. 从 Forge maven 下载 mcp 的 joined.srg、mcp_stable-22 的 CSV、Forge userdev 里的 merged_at.cfg  (约 4.5 MB，一次性)
#   2. 用本机已安装的 1.8.9 客户端 jar + Forge universal jar 作为输入
#   3. 编译 build-tools/src，生成 minecraft-1.8.9-forge-mcp.jar 与 mcp2srg-1.8.9.map
#   4. 按本机 .minecraft 的库列表生成 cp.txt
#
# 只需要联网一次；之后 build-local.ps1 完全离线。
param(
    [string]$JavaHome = '',
    [string]$MinecraftDir = (Join-Path $env:APPDATA '.minecraft'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$tools = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProgressPreference = 'SilentlyContinue'

function Resolve-Jdk([string]$p) {
    if ($p -and (Test-Path (Join-Path $p 'bin\javac.exe'))) { return $p }
    foreach ($c in @($env:JAVA_HOME,
                     'D:\deobf\_tools\jdk8\zulu8.78.0.19-ca-jdk8.0.412-win_x64',
                     'C:\Program Files\Java\jdk1.8.0_471',
                     'C:\Program Files\Eclipse Adoptium\jdk8u512-b09',
                     'C:\Program Files\Zulu\zulu-8')) {
        if ($c -and (Test-Path (Join-Path $c 'bin\javac.exe'))) { return $c }
    }
    throw 'JDK 8 not found: pass -JavaHome <path-to-jdk8>'
}
$JavaHome = Resolve-Jdk $JavaHome
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java  = Join-Path $JavaHome 'bin\java.exe'
Write-Host "JDK: $JavaHome"

$dl    = Join-Path $tools 'dl'
$work  = Join-Path $tools 'work'
$out   = Join-Path $tools 'out'
$lib   = Join-Path $tools 'lib'
$sdk   = Join-Path $tools 'minecraft-1.8.9-forge-mcp.jar'
$map   = Join-Path $tools 'mcp2srg-1.8.9.map'
$cpTxt = Join-Path $tools 'cp.txt'
New-Item -ItemType Directory -Force -Path $dl, $work, $out | Out-Null

# ---------------------------------------------------------------- 1. 输入文件
$jarMc    = Join-Path $MinecraftDir 'versions\1.8.9\1.8.9.jar'
if (-not (Test-Path $jarMc)) { throw "找不到 1.8.9 客户端 jar: $jarMc（用 -MinecraftDir 指定 .minecraft 路径）" }

$jarForge = Get-ChildItem (Join-Path $MinecraftDir 'libraries\net\minecraftforge\forge') -Recurse -Filter 'forge-1.8.9-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'userdev|sources' } |
    Sort-Object Length -Descending | Select-Object -First 1
if (-not $jarForge) { throw "找不到 Forge 1.8.9 universal jar（请先安装一次 1.8.9 Forge）" }
Write-Host "client: $jarMc"
Write-Host "forge : $($jarForge.FullName)"

# ---------------------------------------------------------------- 2. 下载映射
function Get-File([string]$url, [string]$dest) {
    if ((Test-Path $dest) -and -not $Force) { return }
    Write-Host "downloading $([IO.Path]::GetFileName($dest)) ..."
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 300
}
Get-File 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip' "$dl\mcp-1.8.9-srg.zip"
Get-File 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/22-1.8.9/mcp_stable-22-1.8.9.zip' "$dl\mcp_stable-22-1.8.9.zip"
Get-File 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-userdev.jar' "$dl\forge-userdev.jar"

Add-Type -AssemblyName System.IO.Compression.FileSystem
function Unzip-Entry([string]$zipPath, [string]$entry, [string]$dest) {
    if ((Test-Path $dest) -and -not $Force) { return }
    $z = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $e = $z.GetEntry($entry)
        if (-not $e) { throw "entry not found: $entry in $zipPath" }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
    } finally { $z.Dispose() }
}
Unzip-Entry "$dl\mcp-1.8.9-srg.zip" 'joined.srg' "$work\joined.srg"
Unzip-Entry "$dl\forge-userdev.jar" 'merged_at.cfg' "$work\merged_at.cfg"
$stable = Join-Path $work 'stable'
if (-not (Test-Path "$stable\fields.csv") -or $Force) {
    [System.IO.Compression.ZipFile]::ExtractToDirectory("$dl\mcp_stable-22-1.8.9.zip", $stable, $true)
}
Write-Host 'mappings ready'

# ---------------------------------------------------------------- 3. 编译工具
$toolCp = ((Get-ChildItem $lib -Filter *.jar | ForEach-Object { $_.FullName }) -join ';')
$sources = Get-ChildItem (Join-Path $tools 'src') -Filter *.java | ForEach-Object { $_.FullName }
if (-not (Test-Path (Join-Path $out 'SdkBuilder.class')) -or $Force) {
    Write-Host 'compiling build tools ...'
    & $javac -encoding UTF-8 -nowarn -d $out -cp $toolCp @sources
    if ($LASTEXITCODE -ne 0) { throw 'build tools compilation failed' }
}

# ---------------------------------------------------------------- 4. SDK + reobf 表
if (-not (Test-Path $sdk) -or $Force) {
    Write-Host 'building compile SDK (this takes ~30 s) ...'
    & $java -Xmx3G -cp "$toolCp;$out" SdkBuilder $jarMc $jarForge.FullName "$work\joined.srg" $stable "$work\merged_at.cfg" $sdk
    if ($LASTEXITCODE -ne 0) { throw 'SDK build failed' }
}
if (-not (Test-Path $map) -or $Force) {
    Write-Host 'generating MCP -> SRG map ...'
    & $java -Xmx3G -cp "$toolCp;$out" GenReobf189 "$work\joined.srg" $stable $sdk $map
    if ($LASTEXITCODE -ne 0) { throw 'reobf map generation failed' }
}

# ---------------------------------------------------------------- 5. classpath
$versionJson = Get-ChildItem (Join-Path $MinecraftDir 'versions') -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like '1.8.9-Forge*' } |
    ForEach-Object { Join-Path $_.FullName ($_.Name + '.json') } |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $versionJson) { $versionJson = Join-Path $MinecraftDir 'versions\1.8.9\1.8.9.json' }
if (-not (Test-Path $versionJson)) { throw "找不到版本 json: $versionJson" }

$libsRoot = Join-Path $MinecraftDir 'libraries'
$json = Get-Content $versionJson -Raw | ConvertFrom-Json
# 编译 SDK 必须排在第一位，并且要排除 Forge 的 runtime(universal) jar：
# 那个 jar 的类名是 notch 命名空间（运行期 FML 才做映射），会把 MCP 名覆盖成 axu/avb 之类。
$paths = New-Object System.Collections.Generic.List[string]
$paths.Add($sdk)
foreach ($l in $json.libraries) {
    if ($l.name -match 'natives') { continue }
    if ($l.name -match '^net\.minecraftforge:forge:') { continue }
    $p = $l.name.Split(':')
    $rel = ($p[0] -replace '\.', '\') + "\$($p[1])\$($p[2])\$($p[1])-$($p[2]).jar"
    $full = Join-Path $libsRoot $rel
    if (Test-Path $full) { $paths.Add($full) }
}
$paths | Set-Content $cpTxt -Encoding ASCII
Write-Host "classpath: $($paths.Count) entries -> $cpTxt"
Write-Host 'OK: toolchain ready'
