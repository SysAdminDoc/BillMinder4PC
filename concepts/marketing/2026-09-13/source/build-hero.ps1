$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$source = Join-Path $PSScriptRoot 'hero.svg'
$logo = Join-Path $root 'assets\brand\billminder4pc-logo.png'
$bills = Join-Path $root 'docs\screenshots\v0.2.2\bills.png'
$calendar = Join-Path $root 'docs\screenshots\v0.2.2\calendar.png'
$output = Join-Path $root 'assets\marketing\billminder4pc-hero.png'

& magick $source `
    '(' $logo -resize '104x104' ')' -geometry '+76+64' -composite `
    '(' $bills -resize '868x589!' ')' -geometry '+652+132' -composite `
    '(' $calendar -resize '420x285!' ')' -geometry '+1080+568' -composite `
    $output

if ($LASTEXITCODE -ne 0) {
    throw "Hero rendering failed with exit code $LASTEXITCODE."
}
