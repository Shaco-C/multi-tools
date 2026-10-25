param()
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$provenance = Get-Content -LiteralPath (Join-Path $taskRoot 'content/fitness/sources/free-exercise-db/provenance.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($provenance.revision -notmatch '^[a-f0-9]{40}$' -or $provenance.repository -ne 'https://github.com/yuhonas/free-exercise-db') { throw 'Unexpected source provenance.' }
$expectedArchive = "content/fitness/source-archives/free-exercise-db-$($provenance.revision).zip"
if ($provenance.sourceArchive.path -ne $expectedArchive) { throw 'Unexpected archive destination; only the project source-archives directory is allowed.' }
$archive = Join-Path $taskRoot $provenance.sourceArchive.path
function Test-Archive {
    param([string]$Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    return (Get-Item -LiteralPath $Path).Length -eq $provenance.sourceArchive.bytes -and
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $provenance.sourceArchive.sha256
}
if (Test-Archive $archive) { Write-Output "Verified existing source archive: $archive"; exit 0 }
if (Test-Path -LiteralPath $archive) { throw 'Existing source archive failed verification; preserve it and investigate before replacing it.' }
# Recreate the original selective git archive; GitHub download ZIPs have a different layout/hash.
# Keep the download cache inside the ignored project output directory, on the same drive.
$cache = Join-Path $taskRoot 'prototype/dist/fitness-acquisition/source-download.git'
New-Item -ItemType Directory -Path $cache -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $archive) -Force | Out-Null
& git init --bare --quiet $cache
if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize download cache.' }
& git -C $cache fetch --depth=1 --no-tags ($provenance.repository + '.git') $provenance.revision
if ($LASTEXITCODE -ne 0) { throw 'Cannot download the pinned source revision.' }
$partial = Join-Path $taskRoot 'prototype/dist/fitness-acquisition/source-archive.partial.zip'
& git -C $cache archive --format=zip "--prefix=free-exercise-db-$($provenance.revision)/" "--output=$partial" $provenance.revision dist/exercises.json exercises LICENSE.md schema.json
if ($LASTEXITCODE -ne 0) { throw 'Cannot generate source archive.' }
if (!(Test-Archive $partial)) { throw 'Downloaded archive does not match the recorded size and SHA-256; it has not been installed.' }
Move-Item -LiteralPath $partial -Destination $archive
Write-Output "Verified and restored source archive: $archive"
