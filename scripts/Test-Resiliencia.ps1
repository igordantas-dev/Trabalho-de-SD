param([string]$BaseUrl = "http://localhost:8081")
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')
$stopped = $false
Push-Location (Split-Path $PSScriptRoot -Parent)

function Get-ApiStatus([string]$Route) {
    try {
        $response = Invoke-WebRequest "$BaseUrl/api/$Route" -Headers $headers -UseBasicParsing -TimeoutSec 15
        return [int]$response.StatusCode
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return [int]$_.Exception.Response.StatusCode
    }
}

try {
    $auth = Invoke-RestMethod "$BaseUrl/api/gerarToken" -TimeoutSec 15
    $headers = @{ Authorization = "Bearer $($auth.token)" }
    $alunos = @(Invoke-RestMethod "$BaseUrl/api/listarAlunos" -Headers $headers -TimeoutSec 15)
    if ($alunos.Count -eq 0 -or -not $alunos[0].id) {
        throw "Execute Test-Gymflow.ps1 primeiro para criar o aluno de teste."
    }
    $financeRoute = "listarMensalidadesAluno/$($alunos[0].id)"
    if ((Get-ApiStatus $financeRoute) -ne 200) { throw "Financeiro ja esta indisponivel." }
    Write-Host "[OK] Alunos e financeiro disponiveis" -ForegroundColor Green

    docker compose stop financeiro-service
    if ($LASTEXITCODE -ne 0) { throw "Falha ao parar financeiro-service." }
    $stopped = $true

    $status = Get-ApiStatus $financeRoute
    if ($status -notin @(503, 504)) { throw "Esperado 503/504 durante falha; recebido $status." }
    Write-Host "[OK] Financeiro indisponivel retornou HTTP $status" -ForegroundColor Green

    if ((Get-ApiStatus "listarAlunos") -ne 200) {
        throw "Consulta de alunos foi afetada pela falha do financeiro."
    }
    Write-Host "[OK] Alunos continuam funcionando durante a falha" -ForegroundColor Green

    docker compose logs --since 2m --tail 100 api-gateway
    Write-Host "Confira nos logs as linhas method=...FinanceiroService/... attempt=1, 2, 3."

    docker compose start financeiro-service
    if ($LASTEXITCODE -ne 0) { throw "Falha ao reiniciar financeiro-service." }
    $recovered = $false
    for ($i = 0; $i -lt 20; $i++) {
        if ((Get-ApiStatus $financeRoute) -eq 200) {
            $recovered = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $recovered) { throw "Financeiro nao recuperou no intervalo esperado." }
    $stopped = $false
    Write-Host "[OK] Financeiro recuperou sem reiniciar o Gateway" -ForegroundColor Green
    Write-Host "TESTE DE RESILIENCIA CONCLUIDO" -ForegroundColor Green
} finally {
    if ($stopped) {
        docker compose start financeiro-service
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Restaure manualmente: docker compose start financeiro-service"
        }
    }
    Pop-Location
}
