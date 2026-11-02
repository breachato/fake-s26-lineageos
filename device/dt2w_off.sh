RC=/vendor/etc/init/zz_oem_bootanim.rc
# the TDDI chip loses power with the display, so gesture mode only produces I2C errors: off
echo gesture > /proc/ilitek/ioctl; sleep 0.3
dmesg | grep -oE 'gesture = [01]' | tail -1
mount -o remount,rw /vendor || exit 1
L=$(ls -Z $RC | awk '{print $1}')
awk '
/^# double tap to wake/ { skip = 3; next }
skip > 0 { skip--; next }
{ print }
' $RC > /data/local/tmp/rc.new
cp /data/local/tmp/rc.new $RC; chcon $L $RC
sync; mount -o remount,ro /vendor
grep -c ilitek $RC
