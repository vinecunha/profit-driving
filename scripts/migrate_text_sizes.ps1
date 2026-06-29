param(
    [string]$LayoutDir = "..\app\src\main\res\layout",
    [string]$ValuesDir = "..\app\src\main\res\values",
    [switch]$WhatIf
)

$sizes = @(9,10,11,12,13,14,15,16,17,18,20,22,24,28,32,48,56)
$changed = @()

# Process layout XML files
Get-ChildItem -Path $LayoutDir -Filter "*.xml" | ForEach-Object {
    $path = $_.FullName
    $content = [System.IO.File]::ReadAllText($path)
    $original = $content
    foreach ($sp in $sizes) {
        $search = 'android:textSize="{0}sp"' -f $sp
        $replace = 'android:textSize="@dimen/text_size_{0}"' -f $sp
        $content = $content -replace [regex]::Escape($search), $replace
    }
    if ($content -ne $original) {
        if (-not $WhatIf) {
            [System.IO.File]::WriteAllText($path, $content)
        }
        $changed += $_.Name
    }
}

# Process styles.xml and themes.xml
$styleFiles = @("styles.xml", "themes.xml")
foreach ($f in $styleFiles) {
    $path = Join-Path -Path $ValuesDir -ChildPath $f
    if (Test-Path $path) {
        $content = [System.IO.File]::ReadAllText($path)
        $original = $content
        foreach ($sp in $sizes) {
            $search = '<item name="android:textSize">{0}sp</item>' -f $sp
            $replace = '<item name="android:textSize">@dimen/text_size_{0}</item>' -f $sp
            $content = $content -replace [regex]::Escape($search), $replace
        }
        if ($content -ne $original) {
            if (-not $WhatIf) {
                [System.IO.File]::WriteAllText($path, $content)
            }
            $changed += $f
        }
    }
}

Write-Host "=== Migration complete ==="
if ($changed.Count -eq 0) {
    Write-Host "No files changed."
} else {
    Write-Host "Files modified ($($changed.Count)):"
    $changed | ForEach-Object { Write-Host "  - $_" }
}
