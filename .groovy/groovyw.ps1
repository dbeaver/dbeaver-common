$ErrorActionPreference = "Stop"

$scriptDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Definition)).Path

function Get-Property {
    param (
        [Parameter(Mandatory)]
        [string]$PropertyName
    )

    $propertiesFile = Join-Path $scriptDir "properties.toml"
    $propertyLine = Get-Content $propertiesFile | Where-Object { $_ -match "^$PropertyName" }
    if ($propertyLine) {
        $propertyValue = $propertyLine -replace '.*= "(.*)".*', '$1'
        return $propertyValue
    }
    return $null
}

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

$groovyVersion = Get-Property "groovy.version"
$groovyDir = Join-Path $scriptDir "groovy-$groovyVersion"

if (-Not (Test-Path $groovyDir)) {
    Write-Host "Downloading Groovy $groovyVersion..."
    $urlPrefix = Get-Property "groovy.downloadUrlPrefix"
    $groovyDownloadUrl = "$urlPrefix-$groovyVersion.zip"
    $zipFile = Join-Path $scriptDir "groovy.zip"
    Invoke-FastFileDownload -Uri $groovyDownloadUrl -OutFile $zipFile
    Write-Host "Unpacking Groovy $groovyVersion..."
    Expand-Archive -Path $zipFile -DestinationPath $scriptDir -Force
    Remove-Item $zipFile
}

$groovy = Join-Path $groovyDir -ChildPath "bin" | Join-Path -ChildPath "groovy.bat"
& $groovy $args
