$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = "$PSScriptRoot\src"
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

$action = {
    $path = $Event.SourceEventArgs.FullPath
    $changeType = $Event.SourceEventArgs.ChangeType
    if ($path -like "*.java") {
        Write-Host "[$(Get-Date -Format HH:mm:ss)] $changeType : $path" -ForegroundColor Yellow
        Write-Host "Building..." -ForegroundColor Cyan
        Set-Location $Event.MessageData
        $result = .\gradlew.bat build -q 2>&1
        if ($LASTEXITCODE -eq 0) {
            Copy-Item -Path "build\libs\onetap-1.0.0.jar" -Destination "$env:APPDATA\.tlauncher\legacy\Minecraft\game\mods\onetap-1.0.0.jar" -Force
            Write-Host "[$(Get-Date -Format HH:mm:ss)] Jar updated!" -ForegroundColor Green
        } else {
            Write-Host "[$(Get-Date -Format HH:mm:ss)] Build failed!" -ForegroundColor Red
            $result | Select-String -Pattern "error:" | ForEach-Object { Write-Host $_ -ForegroundColor Red }
        }
    }
}

Register-ObjectEvent $watcher "Changed" -Action $action -MessageData $PSScriptRoot
Register-ObjectEvent $watcher "Created" -Action $action -MessageData $PSScriptRoot

Write-Host "Watching src/ for changes... (Ctrl+C to stop)" -ForegroundColor Cyan
while ($true) { Start-Sleep 1 }
