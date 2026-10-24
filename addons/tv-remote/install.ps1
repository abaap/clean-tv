# Windows version of install.sh: install TV Remote on the TV and give it what it needs.
#
# Usage: addons\tv-remote\install.ps1 -TvHost 192.168.1.50 -RelayUrl https://tv-remote.<you>.workers.dev   # install, point it at your relay
#        addons\tv-remote\install.ps1 -TvHost 192.168.1.50      # install the APK built earlier (see the README)
#        addons\tv-remote\install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$RelayUrl = $env:RELAY_URL,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.ptrinh.tvremote'
$svc = "$pkg/$pkg.KeyService"
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

# No double quotes inside these commands: Windows PowerShell 5.1 mangles them on the way to python, and the
# values have no spaces.
if ($Uninstall) {
    Tv ('s=$(settings get secure enabled_accessibility_services); s=$(echo :$s: | sed s#:SVC:#:#); s=${s#:}; s=${s%:}; if [ -z $s ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services $s; fi; pm uninstall PKG'.Replace('SVC', $svc).Replace('PKG', $pkg))
    exit 0
}

if (-not (Test-Path $apk)) {
    Push-Location $here
    # javac notes go to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop'.
    $ErrorActionPreference = 'Continue'
    try {
        & .\gradlew.bat assembleRelease -q
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed)' }
    $ErrorActionPreference = 'Stop'
}
if (-not (Test-Path $apk)) { throw 'no APK: build it with gradlew.bat assembleRelease' }

& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/tvremote.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/tvremote.apk; rm -f /data/local/tmp/tvremote.apk'

Write-Host 'Display over other apps: the lock timer countdown, and opening the message, lock and live-view screens over other apps.'
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow"
Write-Host 'Screen capture without a prompt (PROJECT_MEDIA): the live view, only while a paired phone watches.'
Tv "appops set $pkg PROJECT_MEDIA allow"
Write-Host 'Usage access (GET_USAGE_STATS): Screen time by app.'
Tv "appops set $pkg GET_USAGE_STATS allow"
Write-Host 'Write secure settings: ADB on/off from the phone.'
Tv "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
Write-Host 'Accessibility service (TV Remote keyboard): typing from the phone, Back/Home, standby without IP control.'
Tv ('s=$(settings get secure enabled_accessibility_services); case :$s: in *:SVC:*) ;; *) if [ -z $s ] || [ $s = null ]; then s=SVC; else s=$s:SVC; fi; settings put secure enabled_accessibility_services $s;; esac; settings put secure accessibility_enabled 1'.Replace('SVC', $svc))
# TCL's auto-start gate (the op doesn't exist elsewhere), so the connection comes back after a reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
if ($RelayUrl) {
    Write-Host "Relay: $RelayUrl (saved on the TV; a new one only needs this script again, no rebuild)."
    $r = Tv "am broadcast -n $pkg/.RelayReceiver --es url '$RelayUrl'"
    if (-not ($r -match 'result=0')) { Write-Warning "the TV refused the relay address $RelayUrl" }
}
Tv "am start -n $pkg/.MainActivity >/dev/null; echo opened"
Write-Host 'Installed. TV Remote is open on the TV: scan the QR code with your phone. Press Home and answer Show card for the card.'
