RC=/vendor/etc/init/zz_oem_bootanim.rc
OV=/vendor/overlay/BreachatoCorners
L_RC=$(ls -Z $RC | awk '{print $1}')
L_OV=$(ls -Z /vendor/overlay/FrameworkResOverlay/FrameworkResOverlay.apk | awk '{print $1}')
mount -o remount,rw /vendor || exit 1
# GPS chip: nobody may open the driver node (mnld/agpsd are already disabled)
# the node is re-created after the FDE framework restart, so lock it once boot completes
grep -q stpgps $RC || printf '\n# GPS hardware hard-off: driver node unreadable\non property:sys.boot_completed=1\n    chmod 0000 /dev/stpgps\n' >> $RC
chcon $L_RC $RC
# rounded corners overlay on framework-res
mkdir -p $OV
cp /data/local/tmp/corners.apk $OV/BreachatoCorners.apk
chmod 755 $OV; chmod 644 $OV/BreachatoCorners.apk
chcon u:object_r:vendor_overlay_file:s0 $OV
chcon $L_OV $OV/BreachatoCorners.apk
sync; mount -o remount,ro /vendor
chmod 0000 /dev/stpgps
# no real network location: Google's Wi-Fi/cell lookup service off, no settings bypass
pm disable com.google.android.gms/com.google.android.location.network.NetworkLocationService
settings put global location_ignore_settings_package_whitelist ""
settings put global wifi_scan_always_enabled 0
settings put global ble_scan_always_enabled 0
settings put global assisted_gps_enabled 0
echo "--- rc"; cat $RC
echo "--- overlay"; ls -lZR $OV
echo "--- stpgps"; ls -l /dev/stpgps
