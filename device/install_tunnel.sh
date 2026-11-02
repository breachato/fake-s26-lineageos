T=/data/local/tmp
WG=com.wireguard.android
RC=/vendor/etc/init/zz_oem_bootanim.rc

echo "=== WireGuard: tunnels and settings"
uid=$(pm list packages -U $WG | grep "^package:$WG " | sed 's/.*uid://')
base=/data/data/$WG
[ -z "$uid" ] && { echo "WireGuard not installed"; exit 1; }
am force-stop $WG
ctx=$(ls -Zd $base | awk '{print $1}')
mkdir -p $base/files/datastore
cp $T/wg/*.conf $base/files/
cp $T/wg/settings.preferences_pb $base/files/datastore/
chown -R $uid:$uid $base/files; chcon -R $ctx $base/files
chmod 700 $base/files $base/files/datastore; chmod 600 $base/files/*.conf $base/files/datastore/*
ls $base/files/ | tr '\n' ' '; echo
rm -rf $T/wg                                    # private keys: keep them only inside WireGuard

echo "=== always-on VPN with lockdown (nothing leaves outside the tunnel)"
appops set $WG ACTIVATE_VPN allow
settings put secure always_on_vpn_app $WG
settings put secure always_on_vpn_lockdown 1
echo "always_on=$(settings get secure always_on_vpn_app) lockdown=$(settings get secure always_on_vpn_lockdown)"

echo "=== system apps and tunnel daemon"
mount -o remount,rw /vendor || exit 1
for a in Tunnel:tunnel AppLock:applock; do
    dir=/vendor/app/${a%%:*}
    mkdir -p $dir
    cp $T/${a#*:}.apk $dir/${a%%:*}.apk
    chmod 755 $dir; chmod 644 $dir/*.apk; chown root:shell $dir
    chcon u:object_r:vendor_app_file:s0 $dir $dir/*.apk
done
cp $T/breachato-tunneld.sh /vendor/bin/breachato-tunneld.sh
chmod 755 /vendor/bin/breachato-tunneld.sh; chcon u:object_r:vendor_file:s0 /vendor/bin/breachato-tunneld.sh
if ! grep -q breachato_tunneld $RC; then
    L=$(ls -Z $RC | awk '{print $1}')
    printf '\n# Tunnel: random Wi-Fi MAC per connection + WireGuard server rotation\nservice breachato_tunneld /system/bin/sh /vendor/bin/breachato-tunneld.sh\n    user root\n    group root\n    seclabel u:r:phhsu_daemon:s0\n    disabled\n\non property:sys.boot_completed=1\n    start breachato_tunneld\n' >> $RC
    chcon $L $RC
fi
sync; mount -o remount,ro /vendor
ls -lZ /vendor/app/Tunnel /vendor/app/AppLock /vendor/bin/breachato-tunneld.sh | grep -v '^total'
tail -9 $RC

echo "=== PIN lock accessibility service (active once the package is scanned at boot)"
settings put secure enabled_accessibility_services it.breachato.lock/it.breachato.lock.LockService
settings put secure accessibility_enabled 1
echo "files app: $(pm list packages com.android.documentsui)"

echo "=== package cache (so the platform-signed apps get their permissions)"
rm -rf /data/system/package_cache/*
echo done
