param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [ValidateSet("sideload", "full")]
    [string]$ExpectedEdition = "sideload"
)

$ErrorActionPreference = "Stop"

$sdkCandidates = @(
    @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { $_ -and (Test-Path $_) }
)

if ($sdkCandidates.Count -eq 0) {
    throw "Android SDK not found in ANDROID_SDK_ROOT, ANDROID_HOME, or LOCALAPPDATA\Android\Sdk."
}
$sdk = $sdkCandidates[0]

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) {
        $env:JAVA_HOME = $jbr
    }
}

$aapt2 = Get-ChildItem "$sdk\build-tools\*\aapt2.exe" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
$dexdump = Get-ChildItem "$sdk\build-tools\*\dexdump.exe" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
$apksigner = Get-ChildItem "$sdk\build-tools\*\apksigner.bat" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName

$manifestOut = (& $aapt2 dump xmltree $ApkPath --file AndroidManifest.xml) -join "`n"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$tmpDexDir = Join-Path ([System.IO.Path]::GetTempPath()) ("aurora_dex_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tmpDexDir | Out-Null
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ApkPath).Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '^classes\d*\.dex$') {
                $dest = Join-Path $tmpDexDir $entry.FullName
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
            }
        }
    } finally {
        $zip.Dispose()
    }
    $dexBuilder = New-Object System.Text.StringBuilder
    Get-ChildItem "$tmpDexDir\classes*.dex" | ForEach-Object {
        $chunk = (& $dexdump $_.FullName) -join "`n"
        [void]$dexBuilder.AppendLine($chunk)
    }
    $dexOut = $dexBuilder.ToString()
} finally {
    Remove-Item -Recurse -Force $tmpDexDir -ErrorAction SilentlyContinue
}
$sigOut = (& $apksigner verify --verbose --print-certs $ApkPath) -join "`n"

$efpNotifManifest = ([regex]::Matches($manifestOut, "BIND_NOTIFICATION_LISTENER_SERVICE")).Count
$efpAccManifest = ([regex]::Matches($manifestOut, "BIND_ACCESSIBILITY_SERVICE")).Count
$efpSmsManifest = ([regex]::Matches($manifestOut, "READ_SMS|RECEIVE_SMS")).Count
$contactsManifest = ([regex]::Matches($manifestOut, "android\.permission\.READ_CONTACTS")).Count
$netOrQueryAll = ([regex]::Matches($manifestOut, "android\.permission\.(INTERNET|QUERY_ALL_PACKAGES)")).Count
$notifDexSuperclass = ([regex]::Matches($dexOut, "Superclass\s*:\s*'Landroid/service/notification/NotificationListenerService;'")).Count

$v1 = $sigOut -match "Verified using v1 scheme \(JAR signing\): true"
$v2 = $sigOut -match "Verified using v2 scheme \(APK Signature Scheme v2\): true"
$v3 = $sigOut -match "Verified using v3 scheme \(APK Signature Scheme v3\): true"
$isDebugCert = $sigOut -match "CN=Android Debug"
$certLine = ($sigOut -split "`r?`n" | Where-Object { $_ -match "certificate DN:" } | Select-Object -First 1).Trim()

$size = (Get-Item $ApkPath).Length
$sha256 = (Get-FileHash -Algorithm SHA256 $ApkPath).Hash.ToLower()

Write-Output "FILE=$(Split-Path -Leaf $ApkPath) EDITION=$ExpectedEdition BYTES=$size SHA256=$sha256"
Write-Output "MANIFEST: BIND_NOTIFICATION_LISTENER_SERVICE=$efpNotifManifest BIND_ACCESSIBILITY_SERVICE=$efpAccManifest SMS=$efpSmsManifest READ_CONTACTS=$contactsManifest INTERNET_OR_QUERY_ALL=$netOrQueryAll"
Write-Output "DEX: NotificationListenerService_Superclass=$notifDexSuperclass"
Write-Output "SIGNING: v1=$v1 v2=$v2 v3=$v3 isDebugCert=$isDebugCert ($certLine)"

$failed = $false
if ($efpAccManifest -ne 0 -or $efpSmsManifest -ne 0 -or $netOrQueryAll -ne 0) { $failed = $true }
if (-not ($v2 -and $v3) -or $isDebugCert) { $failed = $true }

if ($ExpectedEdition -eq "sideload") {
    if ($efpNotifManifest -ne 0 -or $contactsManifest -ne 0 -or $notifDexSuperclass -ne 0) { $failed = $true }
} else {
    if ($efpNotifManifest -lt 1 -or $contactsManifest -lt 1 -or $notifDexSuperclass -lt 1) { $failed = $true }
}

if ($failed) {
    Write-Output "VERDICT=FAIL"
    exit 1
} else {
    Write-Output "VERDICT=PASS"
    exit 0
}
