# Builds the report: SVG figures -> PNG (for Word), report.html -> PDF (Edge), report.html -> DOCX (Word).
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$name = 'Comment-Aware-Change-History-CodeTracker'
$profileDir = Join-Path $env:TEMP 'ct-report-edge-profile'
New-Item -ItemType Directory -Force $profileDir | Out-Null

function Invoke-Edge([string[]]$arguments) {
    $common = @('--headless=new', '--disable-gpu', '--no-first-run', '--hide-scrollbars', "--user-data-dir=$profileDir")
    $p = Start-Process -FilePath $edge -ArgumentList ($common + $arguments) -PassThru -WindowStyle Hidden
    if (-not $p.WaitForExit(120000)) { $p.Kill(); throw "Edge timed out: $arguments" }
}

# 1. Figures: SVG -> PNG at 2x, using each SVG's own width/height.
Get-ChildItem (Join-Path $here 'figures') -Filter *.svg | ForEach-Object {
    [xml]$svg = Get-Content $_.FullName -Raw
    $w = [int]$svg.svg.width; $h = [int]$svg.svg.height
    $png = [IO.Path]::ChangeExtension($_.FullName, '.png')
    Invoke-Edge @('--force-device-scale-factor=2', "--window-size=$w,$h", '--default-background-color=FFFFFFFF', "--screenshot=$png", ([Uri]$_.FullName).AbsoluteUri)
    Write-Host "figure: $($_.Name) -> $(Split-Path -Leaf $png)"
}

# 2. PDF from the HTML (vector figures).
$html = Join-Path $here 'report.html'
$pdf = Join-Path $here "$name.pdf"
if (Test-Path $pdf) { Remove-Item $pdf }
Invoke-Edge @('--no-pdf-header-footer', "--print-to-pdf=$pdf", ([Uri]$html).AbsoluteUri)
Write-Host "pdf: $pdf ($([int]((Get-Item $pdf).Length / 1KB)) KB)"

# 3. DOCX through Word: a copy of the HTML that uses the PNG figures.
$wordHtml = Join-Path $here 'report-word.html'
(Get-Content $html -Raw -Encoding UTF8) -replace '(figures/[^"]+)\.svg"', '$1.png"' | Set-Content $wordHtml -Encoding UTF8
$docx = Join-Path $here "$name.docx"
if (Test-Path $docx) { Remove-Item $docx }
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($wordHtml, $false, $true)
    $doc.ActiveWindow.View.Type = 3   # print layout
    $ps = $doc.PageSetup
    $ps.PaperSize = 7                 # A4
    $ps.TopMargin = 68; $ps.BottomMargin = 68; $ps.LeftMargin = 65; $ps.RightMargin = 65
    $textWidth = $ps.PageWidth - $ps.LeftMargin - $ps.RightMargin
    foreach ($shape in @($doc.InlineShapes)) {
        if ($shape.LinkFormat -ne $null) {
            $shape.LinkFormat.SavePictureWithDocument = $true
            $shape.LinkFormat.BreakLink()
        }
    }
    foreach ($shape in @($doc.InlineShapes)) {
        if ($shape.Width -gt $textWidth) {
            $ratio = $textWidth / $shape.Width
            $shape.Width = $textWidth; $shape.Height = $shape.Height * $ratio
        }
    }
    # Windows PowerShell 5.1 must pass these by reference, or the call hangs.
    $target = [string]$docx; $format = 16  # wdFormatDocumentDefault (.docx)
    $doc.SaveAs2([ref]$target, [ref]$format)
    $doc.Close([ref]0)                # wdDoNotSaveChanges
    Write-Host "docx: $docx ($([int]((Get-Item $docx).Length / 1KB)) KB)"
}
finally {
    $word.Quit([ref]0)
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word)
    Remove-Item $wordHtml -ErrorAction SilentlyContinue
}
