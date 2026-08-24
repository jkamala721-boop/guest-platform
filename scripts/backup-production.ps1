[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pgDump = 'C:\Program Files\PostgreSQL\18\bin\pg_dump.exe'
$pgRestore = 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe'
$requiredVariables = @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD')

$missingVariables = @(
    foreach ($name in $requiredVariables) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            $name
        }
    }
)

if ($missingVariables.Count -gt 0) {
    Write-Error ("Backup failed: required environment variable(s) are missing: " + ($missingVariables -join ', '))
    exit 1
}

if (-not (Test-Path -LiteralPath $pgDump -PathType Leaf)) {
    Write-Error 'Backup failed: pg_dump.exe was not found at the configured PostgreSQL 18 client-tools path.'
    exit 1
}

if (-not (Test-Path -LiteralPath $pgRestore -PathType Leaf)) {
    Write-Error 'Backup failed: pg_restore.exe was not found beside pg_dump.exe.'
    exit 1
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$backupDirectory = Join-Path $projectRoot 'backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null

$timestamp = Get-Date -Format 'yyyy-MM-dd-HHmmss'
$backupPath = Join-Path $backupDirectory ("hostvero-production-$timestamp.dump")

# PGPASSWORD is supplied only through the process environment. Do not construct or print a connection URI.
& $pgDump `
    '--format=custom' `
    '--no-owner' `
    '--no-privileges' `
    ("--host={0}" -f $env:PGHOST) `
    ("--port={0}" -f $env:PGPORT) `
    ("--username={0}" -f $env:PGUSER) `
    ("--dbname={0}" -f $env:PGDATABASE) `
    ("--file={0}" -f $backupPath) *> $null

if ($LASTEXITCODE -ne 0) {
    Write-Error ("Backup failed: pg_dump exited with code {0}." -f $LASTEXITCODE)
    exit $LASTEXITCODE
}

if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf) -or (Get-Item -LiteralPath $backupPath).Length -le 0) {
    Write-Error 'Backup failed: pg_dump did not create a non-empty archive.'
    exit 1
}

# --list reads the custom archive locally and never connects to the production database.
& $pgRestore '--list' $backupPath *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error ("Backup verification failed: pg_restore exited with code {0}." -f $LASTEXITCODE)
    exit $LASTEXITCODE
}

$fileSize = (Get-Item -LiteralPath $backupPath).Length
Write-Output ("Backup path: {0}" -f $backupPath)
Write-Output ("File size: {0} bytes" -f $fileSize)
Write-Output 'Verification: succeeded'
