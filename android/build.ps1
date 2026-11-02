# Builds corners.apk (vendor RRO) and fakeloc.apk into C:\Users\Lollo\android-sdk\out.
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
Copy-Item -Recurse "$PSScriptRoot\corners", "$PSScriptRoot\fakeloc" $w

if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass breachato -keypass breachato -alias b `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=breachato" 2>&1 | Out-Null
}

function Sign($unsigned, $final) {
    & "$bt\zipalign.exe" -f -p 4 $unsigned "$unsigned.al"
    if ($LASTEXITCODE) { throw "zipalign $unsigned" }
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:breachato --ks-key-alias b --out $final "$unsigned.al"
    if ($LASTEXITCODE) { throw "apksigner $final" }
}

# --- rounded corners overlay
& "$bt\aapt2.exe" compile --dir "$w\corners\res" -o "$w\corners.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile corners' }
& "$bt\aapt2.exe" link -I $jar --manifest "$w\corners\AndroidManifest.xml" -o "$w\corners_u.apk" "$w\corners.zip"
if ($LASTEXITCODE) { throw 'aapt2 link corners' }
Sign "$w\corners_u.apk" "$out\corners.apk"

# --- fake location app
$cls = "$w\classes"; New-Item -ItemType Directory -Force $cls | Out-Null
$srcs = Get-ChildItem -Recurse "$w\fakeloc\src" -Filter *.java | ForEach-Object FullName
& javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath $jar -d $cls @srcs
if ($LASTEXITCODE) { throw 'javac' }
# d8 30.0.3 crashes under JDK 25; dx handles Java 8 class files fine
& java -cp "$bt\lib\dx.jar" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$w\classes.dex" $cls
if ($LASTEXITCODE) { throw 'dx' }
& "$bt\aapt2.exe" link -I $jar --manifest "$w\fakeloc\AndroidManifest.xml" -o "$w\fakeloc_u.apk"
if ($LASTEXITCODE) { throw 'aapt2 link fakeloc' }
Push-Location $w
& "$bt\aapt.exe" add fakeloc_u.apk classes.dex | Out-Null
Pop-Location
if ($LASTEXITCODE) { throw 'aapt add dex' }
Sign "$w\fakeloc_u.apk" "$out\fakeloc.apk"

Get-ChildItem $out -Filter *.apk | Select-Object Name, Length
