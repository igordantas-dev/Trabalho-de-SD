param([string]$BaseUrl = "http://localhost:8081", [PSCredential]$CredencialGestao)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{}
$step = "frontend"

function Invoke-GymflowApi {
    param([string]$Method, [string]$Route, $Data)
    $request = @{
        Uri = "$BaseUrl/api/$Route"; Method = $Method; Headers = $headers
        ContentType = "application/json; charset=utf-8"
        TimeoutSec = 30; ErrorAction = "Stop"
    }
    if ($null -ne $Data) { $request.Body = $Data | ConvertTo-Json -Depth 10 }
    Invoke-RestMethod @request
}

try {
    $page = Invoke-WebRequest "$BaseUrl/" -UseBasicParsing -TimeoutSec 30
    if ($page.Content -notmatch 'GymFlow') { throw "Pagina inesperada." }
    Write-Host "[OK] Frontend respondeu" -ForegroundColor Green

    $step = "token"
    if (-not $CredencialGestao) { $CredencialGestao = Get-Credential -Message "Informe o e-mail e a senha da gestao" }
    $loginBody = @{ username = $CredencialGestao.UserName; senha = $CredencialGestao.GetNetworkCredential().Password; perfil = "ADMIN" } | ConvertTo-Json
    $auth = Invoke-RestMethod "$BaseUrl/api/autenticar" -Method POST -Body $loginBody -ContentType "application/json; charset=utf-8" -TimeoutSec 30
    $loginBody = $null
    if (-not $auth.token) { throw "Token ausente." }
    $headers = @{ Authorization = "Bearer $($auth.token)" }
    Write-Host "[OK] Login da gestao validado pelo frontend e Gateway" -ForegroundColor Green

    $step = "aluno"
    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
    $email = "teste.$suffix@example.com"
    $cpf = -join (1..11 | ForEach-Object { Get-Random -Minimum 0 -Maximum 10 })
    $aluno = Invoke-GymflowApi POST "cadastrarAluno" @{
        nome = "Teste automatico $suffix"; cpf = $cpf; email = $email
        telefone = "62999990000"; peso = 70.0; altura = 1.75; senha = [guid]::NewGuid().ToString("N")
    }
    if (-not $aluno.id) { throw "Cadastro sem ID." }
    $consulta = Invoke-GymflowApi GET "buscarAluno/$($aluno.id)"
    if ($consulta.email -ne $email) { throw "Aluno consultado difere do criado." }
    Write-Host "[OK] Aluno cadastrado e consultado" -ForegroundColor Green

    $step = "treino"
    $ficha = Invoke-GymflowApi POST "criarFichaTreino" @{
        alunoId = $aluno.id; titulo = "Treino de teste $suffix"; objetivo = "HIPERTROFIA"
        itens = @(@{
            nomeExercicio = "Supino"; grupoMuscular = "Peito"
            series = 3; repeticoes = 12; cargaSugerida = 10.0
        })
    }
    if (-not $ficha.id) { throw "Ficha sem ID." }
    $consulta = Invoke-GymflowApi GET "buscarFichaTreino/$($ficha.id)"
    if ($consulta.alunoId -ne $aluno.id -or @($consulta.itens).Count -ne 1) {
        throw "Ficha consultada possui dados inesperados."
    }
    Write-Host "[OK] Treino criado e consultado" -ForegroundColor Green

    $step = "mensalidade"
    $mensalidade = Invoke-GymflowApi POST "gerarMensalidade" @{
        alunoId = $aluno.id; valor = 99.90
        dataVencimento = (Get-Date).AddDays(10).ToString("yyyy-MM-dd")
        descricao = "Mensalidade de teste $suffix"
    }
    if (-not $mensalidade.id -or $mensalidade.status -ne "PENDENTE") {
        throw "Mensalidade nao foi criada como PENDENTE."
    }
    Write-Host "[OK] Mensalidade criada como PENDENTE" -ForegroundColor Green

    $step = "pagamento"
    $null = Invoke-GymflowApi POST "registrarPagamentoMensalidade/$($mensalidade.id)" @{
        formaPagamento = "PIX"
    }
    $consulta = Invoke-GymflowApi GET "buscarMensalidade/$($mensalidade.id)"
    if ($consulta.status -ne "PAGO" -or $consulta.formaPagamento -ne "PIX") {
        throw "Pagamento nao confirmado."
    }
    Write-Host "[OK] Pagamento registrado e consultado como PAGO" -ForegroundColor Green

    Write-Host "`nTODOS OS TESTES PASSARAM" -ForegroundColor Green
    [pscustomobject]@{
        AlunoId = $aluno.id; FichaId = $ficha.id; MensalidadeId = $mensalidade.id
    } | Format-List
} catch {
    Write-Host "[FALHOU] Etapa: $step" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    throw
}
