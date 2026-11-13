# Builds the evaluation guide: SVG figures -> PNG (for the Markdown guide), report.html -> PDF (Edge headless).
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$name = 'Evaluating-CodeTracker'
$profileDir = Join-Path $env:TEMP 'ct-eval-edge'
New-Item -ItemType Directory -Force $profileDir | Out-Null

function Invoke-Edge([string[]]$arguments) {
    $common = @('--headless=new', '--disable-gpu', '--no-first-run', '--hide-scrollbars', "--user-data-dir=$profileDir")
    $p = Start-Process -FilePath $edge -ArgumentList ($common + $arguments) -PassThru -WindowStyle Hidden
    if (-not $p.WaitForExit(120000)) { $p.Kill(); throw "Edge timed out: $arguments" }
}

Get-ChildItem (Join-Path $here 'figures') -Filter *.svg | ForEach-Object {
    [xml]$svg = Get-Content $_.FullName -Raw
    $png = [IO.Path]::ChangeExtension($_.FullName, '.png')
    Invoke-Edge @('--force-device-scale-factor=2', "--window-size=$($svg.svg.width),$($svg.svg.height)", '--default-background-color=FFFFFFFF', "--screenshot=$png", ([Uri]$_.FullName).AbsoluteUri)
    Write-Host "figure: $($_.Name)"
}

$pdf = Join-Path $here "$name.pdf"
if (Test-Path $pdf) {
    try { Remove-Item $pdf -ErrorAction Stop }
    catch {
        # the PDF is open in a viewer: write next to it instead
        $pdf = Join-Path $here "$name-updated.pdf"
        if (Test-Path $pdf) { Remove-Item $pdf }
        Write-Host "note: $name.pdf is open in another program; writing $name-updated.pdf instead"
    }
}
Invoke-Edge @('--no-pdf-header-footer', "--print-to-pdf=$pdf", ([Uri](Join-Path $here 'report.html')).AbsoluteUri)
Write-Host "pdf: $pdf ($([int]((Get-Item $pdf).Length / 1KB)) KB)"
