T=/data/local/tmp
mount -o remount,rw /vendor || exit 1
put() {   # put <apk> <dir> <name> <label>
    mkdir -p $2; cp $T/$1 $2/$3; chmod 755 $2; chmod 644 $2/$3; chcon $4 $2 $2/$3
}
put corners.apk /vendor/overlay/BreachatoCorners BreachatoCorners.apk u:object_r:vendor_overlay_file:s0
put sysui.apk /vendor/overlay/BreachatoSystemUI BreachatoSystemUI.apk u:object_r:vendor_overlay_file:s0
put home.apk /vendor/app/Home Home.apk u:object_r:vendor_app_file:s0
chown root:shell /vendor/app/Home
sync; mount -o remount,ro /vendor
ls -lZ /vendor/overlay/BreachatoSystemUI /vendor/overlay/BreachatoCorners /vendor/app/Home | grep apk
cmd uimode night yes
rm -rf /data/system/package_cache/*
echo staged
