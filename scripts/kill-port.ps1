<#
.SYNOPSIS
    结束占用指定端口的进程(默认 8080 后端 / 8000 AI 服务)。

.DESCRIPTION
    Windows 上按 Ctrl+C 停止 `mvn spring-boot:run` 时,只会结束 Maven 外壳,
    它 fork 出来的 Java 子进程常常残留下来继续占用端口,导致下次启动报
        Web server failed to start. Port 8080 was already in use.
    这个脚本用来清理这类残留。

.PARAMETER Ports
    要释放的端口列表,默认 8080, 8000。

.EXAMPLE
    .\scripts\kill-port.ps1
    .\scripts\kill-port.ps1 -Ports 8080,8000,3306
#>
param(
    # 收 string[] 而非 int[]:兼容 `-Ports 8080,8000`、`-Ports 8080 8000`、`-Ports "8080,8000"`
    # 三种写法。若直接声明为 int[],某些调用方式(如从 bash 传参)会把逗号吃掉拼成 80808000。
    [string[]]$Ports = @('8080', '8000')
)

# 展开逗号分隔写法并转成整数,顺带过滤空值
$Ports = $Ports |
    ForEach-Object { $_ -split ',' } |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -ne '' } |
    ForEach-Object {
        $n = 0
        if ([int]::TryParse($_, [ref]$n) -and $n -ge 1 -and $n -le 65535) { $n }
        else { Write-Host "[!!] 非法端口:$_" -ForegroundColor Red }
    }

if ($Ports.Count -eq 0) {
    Write-Host "没有有效的端口可处理。" -ForegroundColor Red
    exit 1
}

# 本文件存为「UTF-8 with BOM」:Windows PowerShell 5.1 在没有 BOM 时会按 GBK 读取,
# 导致中文被解析成乱码、字符串引号失配、脚本直接语法报错。改动本文件时请保持 BOM。
# 下面两行让中文输出在默认 GBK 控制台里也能正常显示。
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$released = 0

foreach ($port in $Ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host ("[  ] {0,-5} 空闲" -f $port) -ForegroundColor DarkGray
        continue
    }

    # 同一个端口可能被 IPv4/IPv6 两条记录指向同一进程,去重后再处理
    foreach ($ownerPid in ($connections.OwningProcess | Sort-Object -Unique)) {
        $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        if (-not $process) {
            Write-Host ("[  ] {0,-5} PID {1} 已不存在,跳过" -f $port, $ownerPid) -ForegroundColor DarkGray
            continue
        }
        Write-Host ("[->] {0,-5} 被 PID {1} ({2}) 占用,结束中..." -f $port, $ownerPid, $process.ProcessName) -ForegroundColor Yellow
        Stop-Process -Id $ownerPid -Force -ErrorAction SilentlyContinue
        $released++
    }
}

Start-Sleep -Milliseconds 800

Write-Host ""
foreach ($port in $Ports) {
    $still = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($still) {
        Write-Host ("[!!] {0,-5} 仍被占用,可能没有权限,试试以管理员身份运行" -f $port) -ForegroundColor Red
    }
    else {
        Write-Host ("[OK] {0,-5} 已释放" -f $port) -ForegroundColor Green
    }
}

if ($released -eq 0) {
    Write-Host "`n没有需要清理的进程。" -ForegroundColor DarkGray
}
