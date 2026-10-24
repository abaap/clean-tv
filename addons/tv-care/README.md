# TV Care

One screen for the TV's health (English, with an Indonesian translation): RAM and storage as rings, CPU (or the core count where the TV hides
`/proc/stat`), Android's thermal status and uptime, the network with live download and upload speed, a
**Clean RAM** button that closes every app running in the background and says how much memory came back, and the
recently used apps, each closed with OK. It declares a CleanYourTV [module card](../../docs/modules.md) ("TV Care ·
RAM, network, clean-up", red glow).

![TV Care on a TV](screenshot.jpg)

## Permissions

| Permission | Why |
|---|---|
| `KILL_BACKGROUND_PROCESSES` | Clean RAM and Close (`ActivityManager.killBackgroundProcesses`). Only background processes can be stopped; an app with a running service may start again. |
| `PACKAGE_USAGE_STATS` | The recently used apps. Granted once over ADB: `appops set io.github.bayhaqy.tvcare GET_USAGE_STATS allow`. Without it the row says so. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | The network card. |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Android 10+ only shows the Wi-Fi name to apps holding location. Granted over ADB: `pm grant io.github.bayhaqy.tvcare android.permission.ACCESS_FINE_LOCATION`. No location is ever read. |

No `INTERNET`, no background service, no storage access. Readings are taken once a second, only while the screen is
open. Font: Outfit (SIL Open Font License, `FONT-LICENSE-OFL.txt`).

## Build and install

JDK 17+ and Android SDK 34, as for the launcher:

```bash
cd addons/tv-care && ./gradlew testReleaseUnitTest lintRelease assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/b64push.py addons/tv-care/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk; appops set io.github.bayhaqy.tvcare GET_USAGE_STATS allow; pm grant io.github.bayhaqy.tvcare android.permission.ACCESS_FINE_LOCATION'
```

## Tested on

Changhong 43" (Skyworth `shibuya`, MediaTek), Android TV 11, firmware RTO9.240708.002: rings, network card, Clean RAM
(18 MB freed with few apps open), Close on recent apps, card prompt. `/proc/stat` and the thermal zones are closed to
apps on this firmware, so CPU shows the core count and the temperature line shows Android's thermal status.
