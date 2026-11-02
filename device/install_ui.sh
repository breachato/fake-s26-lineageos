T=/data/local/tmp
RC=/vendor/etc/init/zz_oem_bootanim.rc
mount -o remount,rw /vendor || exit 1
for a in FakeLoc:fakeloc AppLock:applock Home:home; do
    dir=/vendor/app/${a%%:*}
    mkdir -p $dir
    cp $T/${a#*:}.apk $dir/${a%%:*}.apk
    chmod 755 $dir; chmod 644 $dir/*.apk; chown root:shell $dir
    chcon u:object_r:vendor_app_file:s0 $dir $dir/*.apk
done
# double tap to wake: switch the ILITEK touch controller's gesture mode on at every boot
if ! grep -q ilitek $RC; then
    L=$(ls -Z $RC | awk '{print $1}')
    printf '\n# double tap to wake (ILITEK gesture mode; the command toggles, off at boot)\non property:sys.boot_completed=1\n    write /proc/ilitek/ioctl gesture\n' >> $RC
    chcon $L $RC
fi
sync; mount -o remount,ro /vendor
ls -lZ /vendor/app/Home /vendor/app/AppLock /vendor/app/FakeLoc | grep apk
tail -4 $RC

# normal Wi-Fi controls off: Wi-Fi is handled by the Tunnel app
T0=$(settings get secure sysui_qs_tiles)
echo "qs tiles before: $T0"
settings put secure sysui_qs_tiles "$(echo "$T0" | tr ',' '\n' | grep -vxE 'wifi|internet|hotspot' | paste -sd, -)"
echo "qs tiles after:  $(settings get secure sysui_qs_tiles)"

rm -rf /data/system/package_cache/*
echo staged
