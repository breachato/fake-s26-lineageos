# Builds corners.apk (vendor RRO), fakeloc.apk and chargeanim.apk into C:\Users\Lollo\android-sdk\out.
# Sources are copied to a short path first: aapt2 chokes on very long Windows paths.
# native tools write progress to stderr; failures are caught via $LASTEXITCODE
$ErrorActionPreference = 'Continue'
$sdk = 'C:\Users\Lollo\android-sdk'
$bt  = "$sdk\bt\android-11"
$jar = "$sdk\pf\android-11\android.jar"
$w   = "$sdk\build"
$out = "$sdk\out"
$ks  = "$sdk\breachato.jks"

Remove-Item -Recurse -Force $w -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $w, $out | Out-Null
Copy-Item -Recurse "$PSScriptRoot\corners", "$PSScriptRoot\fakeloc", "$PSScriptRoot\chargeanim", "$PSScriptRoot\applock", "$PSScriptRoot\tunnel" $w

if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass breachato -keypass breachato -alias b `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=breachato" 2>&1 | Out-Null
}

# platform: sign with AOSP's public platform test key, which this GSI's system is signed with,
# so the app gets signature permissions (STATUS_BAR) and may use hidden APIs
$pk = "$sdk\platformkey"
function Sign($unsigned, $final, $platform = $false) {
    & "$bt\zipalign.exe" -f -p 4 $unsigned "$unsigned.al"
    if ($LASTEXITCODE) { throw "zipalign $unsigned" }
    if ($platform) {
        & "$bt\apksigner.bat" sign --key "$pk\platform.pk8" --cert "$pk\platform.x509.pem" --out $final "$unsigned.al"
    } else {
        & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:breachato --ks-key-alias b --out $final "$unsigned.al"
    }
    if ($LASTEXITCODE) { throw "apksigner $final" }
}

# --- rounded corners overlay
& "$bt\aapt2.exe" compile --dir "$w\corners\res" -o "$w\corners.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile corners' }
& "$bt\aapt2.exe" link -I $jar --manifest "$w\corners\AndroidManifest.xml" -o "$w\corners_u.apk" "$w\corners.zip"
if ($LASTEXITCODE) { throw 'aapt2 link corners' }
Sign "$w\corners_u.apk" "$out\corners.apk"

# --- apps: javac -> dx -> apk (manifest + optional res/) + classes.dex
function BuildApp($name, $platform = $false) {
    $d = "$w\$name"
    $linkRes = @()
    if (Test-Path "$d\res") {
        & "$bt\aapt2.exe" compile --dir "$d\res" -o "$d\res.zip"
        if ($LASTEXITCODE) { throw "aapt2 compile $name" }
        $linkRes = @("$d\res.zip")
    }
    $cls = "$d\classes"; New-Item -ItemType Directory -Force $cls | Out-Null
    # @(): a single source file must still splat as one argument, not as characters
    $srcs = @(Get-ChildItem -Recurse "$d\src" -Filter *.java | ForEach-Object FullName)
    & javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath $jar -d $cls @srcs
    if ($LASTEXITCODE) { throw "javac $name" }
    # d8 30.0.3 crashes under JDK 25; dx handles Java 8 class files fine
    & java -cp "$bt\lib\dx.jar" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$d\classes.dex" $cls
    if ($LASTEXITCODE) { throw "dx $name" }
    & "$bt\aapt2.exe" link -I $jar --manifest "$d\AndroidManifest.xml" -o "$d\${name}_u.apk" @linkRes
    if ($LASTEXITCODE) { throw "aapt2 link $name" }
    Push-Location $d
    & "$bt\aapt.exe" add "${name}_u.apk" classes.dex | Out-Null
    $rc = $LASTEXITCODE
    Pop-Location
    if ($rc) { throw "aapt add dex $name" }
    Sign "$d\${name}_u.apk" "$out\$name.apk" $platform
}

BuildApp fakeloc
BuildApp chargeanim $true
BuildApp applock
BuildApp tunnel $true

Get-ChildItem $out -Filter *.apk | Select-Object Name, Length
