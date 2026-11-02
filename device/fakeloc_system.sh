PKG=it.breachato.fakeloc
DIR=/vendor/app/FakeLoc
# pick the SELinux type vendor apps use, fall back to generic vendor_file
LBL=u:object_r:vendor_app_file:s0
ls -Zd /vendor/app/* 2>/dev/null | head -1 | grep -q vendor_app_file || LBL=u:object_r:vendor_file:s0
mount -o remount,rw /vendor || exit 1
mkdir -p $DIR
cp /data/local/tmp/fakeloc.apk $DIR/FakeLoc.apk
chmod 755 $DIR; chmod 644 $DIR/FakeLoc.apk
chcon u:object_r:vendor_file:s0 $DIR
chcon $LBL $DIR/FakeLoc.apk
sync; mount -o remount,ro /vendor
echo "--- installed"; ls -lZ $DIR
