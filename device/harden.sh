# --- Google services that track or phone home (undo: pm enable <pkg>)
for p in com.google.android.gms com.google.android.gsf com.google.android.gms.location.history \
         com.google.android.apps.restore com.google.android.configupdater com.google.android.feedback \
         com.google.android.onetimeinitializer com.google.android.partnersetup \
         com.google.android.printservice.recommendation com.google.android.projection.gearhead \
         com.google.android.setupwizard com.google.android.syncadapters.calendar \
         com.google.android.syncadapters.contacts com.google.android.ims; do
    pm disable-user --user 0 $p >/dev/null && echo "off: $p"
done

# --- radios and network probes
settings put global captive_portal_mode 0          # no connectivitycheck.gstatic.com probes
settings put global wifi_scan_always_enabled 0
settings put global ble_scan_always_enabled 0
settings put global wifi_wakeup_enabled 0
settings put global network_recommendations_enabled 0
settings put global assisted_gps_enabled 0
content insert --uri content://lineagesettings/secure --bind name:s:stats_collection --bind value:s:0 2>&1 | tail -1
cmd connectivity airplane-mode enable              # cellular modem off; emergency calls still re-enable it
svc bluetooth disable 2>/dev/null
sleep 2
svc wifi enable
echo "airplane=$(settings get global airplane_mode_on) wifi=$(settings get global wifi_on) bt=$(settings get global bluetooth_on) captive=$(settings get global captive_portal_mode)"

# --- stage the new overlay (status-bar slots) and the platform-signed charge app
mount -o remount,rw /vendor || exit 1
cp /data/local/tmp/corners.apk /vendor/overlay/BreachatoCorners/BreachatoCorners.apk
cp /data/local/tmp/chargeanim.apk /vendor/app/ChargeAnim/ChargeAnim.apk
chmod 644 /vendor/overlay/BreachatoCorners/BreachatoCorners.apk /vendor/app/ChargeAnim/ChargeAnim.apk
chcon u:object_r:vendor_overlay_file:s0 /vendor/overlay/BreachatoCorners/BreachatoCorners.apk
chcon u:object_r:vendor_app_file:s0 /vendor/app/ChargeAnim/ChargeAnim.apk
sync; mount -o remount,ro /vendor
ls -lZ /vendor/overlay/BreachatoCorners/ /vendor/app/ChargeAnim/

# --- hide SystemUI's own battery; the charge app draws the new one
B=$(settings get secure icon_blacklist)
case ",$B," in *,battery,*) ;; *) settings put secure icon_blacklist "$B,battery" ;; esac
settings get secure icon_blacklist
