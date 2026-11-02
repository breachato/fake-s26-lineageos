DIR=/vendor/app/ChargeAnim
mount -o remount,rw /vendor || exit 1
mkdir -p $DIR
cp /data/local/tmp/chargeanim.apk $DIR/ChargeAnim.apk
chmod 755 $DIR; chmod 644 $DIR/ChargeAnim.apk
chown root:shell $DIR
# both the folder and the apk need vendor_app_file, or PackageManager skips the folder
chcon u:object_r:vendor_app_file:s0 $DIR $DIR/ChargeAnim.apk
sync; mount -o remount,ro /vendor
ls -lZd $DIR $DIR/ChargeAnim.apk
