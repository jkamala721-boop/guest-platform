[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$BackupPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pgRestore = 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe'

if (-not (Test-Path -LiteralPath $pgRestore -PathType Leaf)) {
    Write-Error 'Verification failed: pg_restore.exe was not found beside pg_dump.exe.'
    exit 1
}

if (-not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) {
    Write-Error 'Verification failed: the backup file was not found.'
    exit 1
}

$backupFile = Get-Item -LiteralPath $BackupPath
if ($backupFile.Length -le 0) {
    Write-Error 'Verification failed: the backup file is empty.'
    exit 1
}

# --list validates the local custom archive only; it has no database connection arguments.
& $pgRestore '--list' $backupFile.FullName *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error ("Verification failed: pg_restore exited with code {0}." -f $LASTEXITCODE)
    exit $LASTEXITCODE
}

Write-Output ("Backup path: {0}" -f $backupFile.FullName)
Write-Output ("File size: {0} bytes" -f $backupFile.Length)
Write-Output 'Verification: succeeded'
