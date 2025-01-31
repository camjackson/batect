Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$Version='VERSION-GOES-HERE'

function getValueOrDefault($value, $default) {
    if ($value -eq $null) {
        $default
    } else {
        $value
    }
}

$DownloadUrlRoot = getValueOrDefault $env:BATECT_DOWNLOAD_URL_ROOT "https://dl.bintray.com/charleskorn/batect"
$UrlEncodedVersion = [Uri]::EscapeDataString($Version)
$DownloadUrl = getValueOrDefault $env:BATECT_DOWNLOAD_URL "$DownloadUrlRoot/$UrlEncodedVersion/bin/batect-$UrlEncodedVersion.jar"

$RootCacheDir = getValueOrDefault $env:BATECT_CACHE_DIR "$env:USERPROFILE\.batect\cache"
$CacheDir = "$RootCacheDir\$Version"
$JarPath = "$CacheDir\batect-$Version.jar"

function main() {
    if (-not (haveVersionCachedLocally)) {
        download
    }

    runApplication @args
}

function haveVersionCachedLocally() {
    Test-Path $JarPath
}

function download() {
    Write-Output "Downloading batect version $Version from $DownloadUrl..."

    createCacheDir

    $oldProgressPreference = $ProgressPreference

    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

        # Turn off the progress bar to significantly reduce download times - see https://github.com/PowerShell/PowerShell/issues/2138#issuecomment-251165868
        $ProgressPreference = 'SilentlyContinue'

        Invoke-WebRequest -Uri $DownloadUrl -OutFile $JarPath | Out-Null
    } catch {
        $Message = $_.Exception.Message

        Write-Host -ForegroundColor Red "Downloading failed with error: $Message"
        exit 1
    } finally {
        $ProgressPreference = $oldProgressPreference
    }
}

function createCacheDir() {
    if (-not (Test-Path $CacheDir)) {
        New-Item -ItemType Directory -Path $CacheDir | Out-Null
    }
}

function runApplication() {
    $java = findJava
    $javaVersion = checkJavaVersion $java

    if ($javaVersion.Major -ge 9) {
        $javaArgs = @("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
    } else {
        $javaArgs = @()
    }

    $combinedArgs = $javaArgs + @("-Djava.net.useSystemProxies=true", "-jar", $JarPath) + $args
    $env:HOSTNAME = $env:COMPUTERNAME

    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $java.Source
    $info.Arguments = combineArgumentsToString($combinedArgs)
    $info.RedirectStandardError = $false
    $info.RedirectStandardOutput = $false
    $info.UseShellExecute = $false

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    $process.Start() | Out-Null
    $process.WaitForExit()

    exit $process.ExitCode
}

function findJava() {
    $java = Get-Command "java" -ErrorAction SilentlyContinue

    if ($java -eq $null) {
        Write-Host -ForegroundColor Red "Java is not installed or not on your PATH. Please install it and try again."
        exit 1
    }

    return $java
}

function checkJavaVersion([System.Management.Automation.CommandInfo]$java) {
    $rawVersion = getJavaVersion $java
    $parsedVersion = New-Object Version -ArgumentList $rawVersion
    $minimumVersion = "1.8"

    if ($parsedVersion -lt (New-Object Version -ArgumentList $minimumVersion)) {
        Write-Host -ForegroundColor Red "The version of Java that is available on your PATH is version $rawVersion, but version $minimumVersion or greater is required."
        Write-Host -ForegroundColor Red "If you have a newer version of Java installed, please make sure your PATH is set correctly."
        exit 1
    }

    return $parsedVersion
}

function getJavaVersion([System.Management.Automation.CommandInfo]$java) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $java.Source
    $info.Arguments = "-version"
    $info.RedirectStandardError = $true
    $info.RedirectStandardOutput = $true
    $info.UseShellExecute = $false

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    $process.Start() | Out-Null
    $process.WaitForExit()

    $stderr = $process.StandardError.ReadToEnd()
    $versionLine = ($stderr -split [Environment]::NewLine)[0]

    if (-not ($versionLine -match "version `"([0-9]+)(\.([0-9]+))?.*`"")) {
        Write-Error "Java reported a version that does not match the expected format: $versionLine"
    }

    $major = $Matches.1

    if ($Matches.Count -ge 3) {
        $minor = $Matches.3
    } else {
        $minor = "0"
    }

    return "$major.$minor"
}

function combineArgumentsToString([Object[]]$arguments) {
    $combined = @()

    $arguments | % { $combined += escapeArgument($_) }

    return $combined -join " "
}

function escapeArgument([String]$argument) {
    return '"' + $argument.Replace('"', '"""') + '"'
}

main @args
