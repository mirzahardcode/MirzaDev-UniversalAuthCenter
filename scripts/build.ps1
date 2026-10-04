$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

$SourceDir    = Join-Path $Root "src"
$ConfigDir    = Join-Path $Root "config"
$GeneratedDir = Join-Path $Root "build\generated"
$ClassesDir   = Join-Path $Root "build\classes"
$DexDir       = Join-Path $Root "out"

$AndroidJar = Join-Path $env:LOCALAPPDATA `
    "Android\Sdk\platforms\android-37.0\android.jar"

$D8 = Join-Path $env:LOCALAPPDATA `
    "Android\Sdk\build-tools\36.0.0\d8.bat"

Write-Host "== UniversalAuthCenter Build =="

if (!(Test-Path $AndroidJar)) {
    throw "android.jar not found: $AndroidJar"
}

if (!(Test-Path $D8)) {
    throw "D8 not found: $D8"
}

$AppProperties = Join-Path $ConfigDir "app.properties"
if (!(Test-Path $AppProperties)) {
    throw "app.properties not found: $AppProperties"
}

$appConfig = Get-Content $AppProperties -Raw
$appKey = $null
foreach ($line in ($appConfig -split "`r?`n")) {
    if ($line -match "^\s*appKey\s*=") {
        $appKey = ($line -split "=", 2)[1].Trim()
        break
    }
}

if ([string]::IsNullOrWhiteSpace($appKey)) {
    throw "appKey is missing or empty in $AppProperties"
}

Remove-Item $GeneratedDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $ClassesDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $DexDir -Recurse -Force -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force $GeneratedDir | Out-Null
New-Item -ItemType Directory -Force $ClassesDir | Out-Null
New-Item -ItemType Directory -Force $DexDir | Out-Null

$GeneratedPackageDir = Join-Path $GeneratedDir "com\universal\authcenter"
New-Item -ItemType Directory -Force $GeneratedPackageDir | Out-Null

$GeneratedJava = Join-Path $GeneratedPackageDir "GeneratedConfig.java"
$GeneratedJavaContent = @"
package com.universal.authcenter;

final class GeneratedConfig {
    static final String APP_KEY = "$appKey";
}
"@
[System.IO.File]::WriteAllText(
    $GeneratedJava,
    $GeneratedJavaContent,
    [System.Text.UTF8Encoding]::new($false)
)

$Sources = @(
    Get-ChildItem $SourceDir -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName }
)

$GeneratedSources = @(
    Get-ChildItem $GeneratedDir -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName }
)

$AllSources = @($GeneratedSources + $Sources)

if ($AllSources.Count -eq 0) {
    throw "No Java source files found."
}

Write-Host "Compiling $($AllSources.Count) Java source(s)..."

javac `
    -source 8 `
    -target 8 `
    -cp $AndroidJar `
    -d $ClassesDir `
    $AllSources

if ($LASTEXITCODE -ne 0) {
    throw "javac failed."
}

$ClassFiles = @(
    Get-ChildItem $ClassesDir -Recurse -Filter "*.class" |
    ForEach-Object { $_.FullName }
)

if ($ClassFiles.Count -eq 0) {
    throw "No .class files were generated."
}

Write-Host "Converting $($ClassFiles.Count) class file(s) to DEX..."

& $D8 `
    --lib $AndroidJar `
    --output $DexDir `
    $ClassFiles

if ($LASTEXITCODE -ne 0) {
    throw "D8 failed."
}

$Dex = Join-Path $DexDir "classes.dex"

if (!(Test-Path $Dex)) {
    throw "classes.dex was not generated."
}

$Size = (Get-Item $Dex).Length

Write-Host ""
Write-Host "BUILD SUCCESS"
Write-Host "Output : $Dex"
Write-Host "Size   : $Size bytes"