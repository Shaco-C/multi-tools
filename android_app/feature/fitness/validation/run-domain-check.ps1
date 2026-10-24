param(
    [Parameter(Mandatory = $true)][string]$JavaExe,
    [Parameter(Mandatory = $true)][string]$KotlinHome,
    [Parameter(Mandatory = $true)][string[]]$RuntimeJars
)

$ErrorActionPreference = 'Stop'
$fitnessRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$fitnessMain = Join-Path $PSScriptRoot '../src/main/kotlin/com/pockettoolbox/feature/fitness'
$fitnessTests = Join-Path $PSScriptRoot '../src/test/kotlin/com/pockettoolbox/feature/fitness/domain'
$fitnessOutput = Join-Path $fitnessRoot 'prototype/dist/fitness-native-check'
New-Item -ItemType Directory -Path $fitnessOutput -Force | Out-Null
foreach ($fitnessPath in @($JavaExe, (Join-Path $KotlinHome 'lib/kotlin-compiler.jar')) + $RuntimeJars) {
    if (-not (Test-Path -LiteralPath $fitnessPath)) { throw "验证依赖不存在：$fitnessPath" }
}
$fitnessSources = @(Get-ChildItem (Join-Path $fitnessMain 'domain') -Filter '*.kt' | ForEach-Object FullName)
$fitnessSources += @('FitnessJson.kt', 'FitnessArchive.kt') | ForEach-Object { Join-Path $fitnessMain "data/$_" }
$fitnessTestFiles = @(Get-ChildItem $fitnessTests -Filter '*.kt')
$fitnessSources += @($fitnessTestFiles | ForEach-Object FullName)
$fitnessLibraries = @((Join-Path $KotlinHome 'lib/kotlin-stdlib.jar'), (Join-Path $KotlinHome 'lib/kotlinx-coroutines-core-jvm.jar')) + $RuntimeJars
$fitnessJar = Join-Path $fitnessOutput 'fitness-domain-tests.jar'
& $JavaExe -version
& $JavaExe -cp "$KotlinHome/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version
& $JavaExe -cp "$KotlinHome/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath ($fitnessLibraries -join [IO.Path]::PathSeparator) "-Xplugin=$KotlinHome/lib/kotlinx-serialization-compiler-plugin.jar" -d $fitnessJar @fitnessSources
if ($LASTEXITCODE -ne 0) { throw '独立JVM规则编译失败' }
$fitnessClasses = foreach ($fitnessFile in $fitnessTestFiles) {
    $fitnessText = Get-Content -LiteralPath $fitnessFile.FullName -Raw
    $fitnessPackage = [regex]::Match($fitnessText, '(?m)^package ([\w.]+)').Groups[1].Value
    foreach ($fitnessMatch in [regex]::Matches($fitnessText, '(?m)^class (\w+Test)\s*\{')) { "$fitnessPackage.$($fitnessMatch.Groups[1].Value)" }
}
$fitnessTemporary = Join-Path $fitnessOutput 'tmp'
New-Item -ItemType Directory -Path $fitnessTemporary -Force | Out-Null
& $JavaExe "-Djava.io.tmpdir=$fitnessTemporary" -cp (($fitnessLibraries + $fitnessJar) -join [IO.Path]::PathSeparator) org.junit.runner.JUnitCore @fitnessClasses
if ($LASTEXITCODE -ne 0) { throw '独立JVM规则测试失败' }
Write-Output '本结果仅为独立JVM规则验证，不是Android/Room/KSP/Compose或APK构建验证。'
