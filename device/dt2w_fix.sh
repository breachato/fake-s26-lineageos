RC=/vendor/etc/init/zz_oem_bootanim.rc
mount -o remount,rw /vendor || exit 1
L=$(ls -Z $RC | awk '{print $1}')
# the toggle must run once per boot: sys.boot_completed fires both before and after the PIN,
# trigger_restart_framework only after it
awk '
/^# double tap to wake/ { skip = 3; next }
skip > 0 { skip--; next }
{ print }
' $RC > /data/local/tmp/rc.new
printf '# double tap to wake (ILITEK gesture mode; the command toggles, off at boot)\non property:vold.decrypt=trigger_restart_framework\n    write /proc/ilitek/ioctl gesture\n' >> /data/local/tmp/rc.new
cp /data/local/tmp/rc.new $RC; chcon $L $RC
sync; mount -o remount,ro /vendor
tail -5 $RC
# and switch it back on now
echo gesture > /proc/ilitek/ioctl; sleep 0.5
dmesg | grep -oE 'gesture = [01]' | tail -1
