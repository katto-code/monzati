# Monzatti · prepara una actualización de la app.
# Compila la APK de release firmada, la copia a dist\ con su nombre de versión y genera el SQL para publicarla.
# Uso (desde la carpeta del proyecto):  .\scripts\publicar-version.ps1 -Notas "• Carrito más rápido`n• Correcciones" [-Obligatoria]

param(
    [string]$Notas = "",
    [switch]$Obligatoria
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$props = Get-Content "$root\gradle.properties" | Where-Object { $_ -match "^monzatti\." }
$code = ($props | Where-Object { $_ -match "^monzatti\.versionCode=" }) -replace ".*=", ""
$name = ($props | Where-Object { $_ -match "^monzatti\.versionName=" }) -replace ".*=", ""

$local = if (Test-Path "$root\local.properties") { Get-Content "$root\local.properties" } else { @() }
if (-not ($local | Where-Object { $_ -match "^RELEASE_STORE_FILE=\S" })) {
    Write-Error "Falta la llave de firma en local.properties (RELEASE_STORE_FILE...). Revisa docs\actualizaciones.md."
}

$supabaseUrl = (($local | Where-Object { $_ -match "^SUPABASE_URL=" }) -replace "^SUPABASE_URL=", "").Trim().TrimEnd("/")
if (-not $supabaseUrl) { Write-Error "Falta SUPABASE_URL en local.properties." }

if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Android\Android Studio\jbr")) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}

Write-Host "Compilando Monzatti $name ($code)..."
& "$root\gradlew.bat" assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { Write-Error "La compilación falló." }

$apk = "$root\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { Write-Error "No se generó app-release.apk (¿la firma está bien configurada?)." }

New-Item -ItemType Directory -Force "$root\dist" | Out-Null
$out = "$root\dist\monzatti-$name.apk"
Copy-Item $apk $out -Force
$sha = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
$mb = [math]::Round((Get-Item $out).Length / 1MB, 1)
if ($mb -gt 50) { Write-Warning "La APK pesa $mb MB: Supabase Storage (plan gratuito) acepta hasta 50 MB." }

$notasSql = $Notas.Replace("'", "''")
$mandatory = if ($Obligatoria) { "true" } else { "false" }
$sql = @"
insert into public.app_releases (channel, version_code, version_name, apk_url, sha256, notes, mandatory)
values ('estable', $code, '$name',
        '$supabaseUrl/storage/v1/object/public/releases/monzatti-$name.apk',
        '$sha', '$notasSql', $mandatory);
"@
$sql | Out-File "$root\dist\publicar-$name.sql" -Encoding utf8

Write-Host ""
Write-Host "Listo: dist\monzatti-$name.apk ($mb MB)"
Write-Host "SHA-256: $sha"
Write-Host ""
Write-Host "Siguientes pasos:"
Write-Host "  1. Supabase -> Storage -> releases -> sube dist\monzatti-$name.apk"
Write-Host "  2. Supabase -> SQL Editor -> ejecuta dist\publicar-$name.sql"
Write-Host "Las apps instaladas verán la actualización la próxima vez que se abran."
