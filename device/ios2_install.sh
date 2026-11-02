T=/data/local/tmp
mount -o remount,rw /vendor || exit 1
cp $T/sysui.apk /vendor/overlay/BreachatoSystemUI/BreachatoSystemUI.apk
chcon u:object_r:vendor_overlay_file:s0 /vendor/overlay/BreachatoSystemUI/BreachatoSystemUI.apk
for a in FakeLoc:fakeloc AppLock:applock; do
    f=/vendor/app/${a%%:*}/${a%%:*}.apk
    cp $T/${a#*:}.apk $f; chmod 644 $f; chcon u:object_r:vendor_app_file:s0 $f
done
sync; mount -o remount,ro /vendor
rm -rf /data/system/package_cache/*
echo staged
