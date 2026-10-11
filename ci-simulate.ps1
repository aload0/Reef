$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

$androidSdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk

$localPropsPath = Join-Path $repoRoot 'local.properties'
if (-not (Test-Path $localPropsPath)) {
    @" 
sdk.dir=C:\Users\$env:USERNAME\AppData\Local\Android\Sdk
"@ | Set-Content -Path $localPropsPath -Encoding UTF8
}

Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"

$candidateRoots = @(
    'C:\Program Files\Eclipse Adoptium',
    'C:\Program Files\Microsoft',
    'C:\Program Files\Java'
)

$resolvedJavaHome = $null
foreach ($root in $candidateRoots) {
    if (Test-Path $root) {
        $matches = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk-21|jdk-21\.' }
        if ($matches) {
            $resolvedJavaHome = $matches[0].FullName
            break
        }
    }
}

if (-not $resolvedJavaHome) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        throw "Java was not found on PATH. Install JDK 21 before running CI simulation."
    }
    $resolvedJavaHome = Split-Path -Parent (Split-Path -Parent $javaCmd.Source)
}

$env:JAVA_HOME = $resolvedJavaHome
Write-Host "JAVA_HOME=$env:JAVA_HOME"

Write-Host "Running CI-like Gradle build..."
& .\gradlew --no-daemon clean test
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Running app assemble check..."
& .\gradlew --no-daemon :Reef:assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "CI simulation completed successfully."
