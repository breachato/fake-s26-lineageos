RC=/vendor/etc/init/zz_oem_bootanim.rc
L_RC=$(ls -Z $RC | awk '{print $1}')
mount -o remount,rw /vendor || exit 1
# the node is re-created after the FDE framework restart, so lock it once boot completes
sed -i 's/^on boot$/on property:sys.boot_completed=1/' $RC
chcon $L_RC $RC
sync; mount -o remount,ro /vendor
chmod 0000 /dev/stpgps
cat $RC; ls -l /dev/stpgps
