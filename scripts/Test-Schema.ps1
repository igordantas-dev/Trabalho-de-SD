$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$dbPath = (Resolve-Path (Join-Path $repoRoot "db")).Path
$containerName = "gymflow-schema-check-" + [guid]::NewGuid().ToString("N")
$started = $false

function Invoke-SchemaFile([string]$RelativeFile) {
    docker exec -e PGPASSWORD=local_schema_test $containerName psql -h 127.0.0.1 -X -U postgres -d academia -v ON_ERROR_STOP=1 -f "/schema/$RelativeFile"
    if ($LASTEXITCODE -ne 0) { throw "Falha no SQL: $RelativeFile" }
}

try {
    docker run --rm --name $containerName -e POSTGRES_DB=academia -e POSTGRES_PASSWORD=local_schema_test --mount "type=bind,source=$dbPath,target=/schema,readonly" -d postgres:16 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel iniciar o PostgreSQL de teste." }
    $started = $true

    $ready = $false
    # The image's temporary initialization server only listens on a Unix socket.
    # TCP becomes available when initialization finishes and the final server starts.
    for ($i = 0; $i -lt 60; $i++) {
        docker exec $containerName pg_isready -h 127.0.0.1 -U postgres -d academia 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "PostgreSQL de teste nao ficou pronto." }

    Invoke-SchemaFile "Apply.psql"
    Write-Host "[OK] Schema criado e validado em banco vazio" -ForegroundColor Green

    Invoke-SchemaFile "tests/Fixture.sql"
    Invoke-SchemaFile "Apply.psql"
    Invoke-SchemaFile "tests/Check-preservation.sql"
    Write-Host "[OK] Reaplicacao preservou registros e versao unica da baseline" -ForegroundColor Green

    docker exec -e PGPASSWORD=local_schema_test $containerName psql -h 127.0.0.1 -X -U postgres -d academia -v ON_ERROR_STOP=1 -c "ALTER TABLE public.alunos RENAME COLUMN nome TO nome_desviado;"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel preparar o teste de divergencia." }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = docker exec -e PGPASSWORD=local_schema_test $containerName psql -h 127.0.0.1 -X -U postgres -d academia -f /schema/Validate.psql 2>&1 | Out-String
        $validationExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($validationExitCode -eq 0 -or $output -notmatch "Schema incompatible") {
        throw "A validacao deveria rejeitar o schema divergente."
    }
    Write-Host "[OK] Schema divergente foi rejeitado" -ForegroundColor Green
    Write-Host "TODOS OS TESTES DO SCHEMA PASSARAM" -ForegroundColor Green
} finally {
    if ($started) {
        # Only the randomly named disposable test container is removed.
        docker rm -f -v $containerName | Out-Null
    }
}
