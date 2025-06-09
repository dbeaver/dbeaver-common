$ErrorActionPreference = "Stop"

$scriptDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Definition)).Path

$groovyVersion = "4.0.27"
$groovyDir = Join-Path $scriptDir "groovy-$groovyVersion"
$groovyDownloadUrl = "https://groovy.jfrog.io/artifactory/dist-release-local/groovy-zips/apache-groovy-binary-$groovyVersion.zip"
$zipFile = Join-Path $scriptDir "groovy.zip"

# https://github.com/PowerShell/PowerShell/issues/13414
function Invoke-FastFileDownload {
    [CmdletBinding()]
    param (
        [Parameter(Mandatory)]
        [string]$Uri,

        [Parameter(Mandatory)]
        [string]$OutFile
    )

    $originalProgressPreference = $ProgressPreference
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $Uri -OutFile $OutFile
    }
    finally {
        $ProgressPreference = $originalProgressPreference
    }
}

if (-Not (Test-Path $groovyDir)) {
    Write-Host "Downloading Groovy $groovyVersion..."
    Invoke-FastFileDownload -Uri $groovyDownloadUrl -OutFile $zipFile
    Write-Host "Unpacking Groovy $groovyVersion..."
    Expand-Archive -Path $zipFile -DestinationPath $scriptDir -Force
    Remove-Item $zipFile
}

$groovy = Join-Path $groovyDir -ChildPath "bin" | Join-Path -ChildPath "groovy.bat"
& $groovy $args
