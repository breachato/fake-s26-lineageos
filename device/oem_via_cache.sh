F=/vendor/etc/fstab.mt6755
LABEL=$(ls -Z $F | awk '{print $1}')
mount -o remount,rw /vendor || { echo "REMOUNT FAILED"; exit 1; }
[ -f /data/local/tmp/fstab.mt6755.orig ] || cp $F /data/local/tmp/fstab.mt6755.orig
# cache partition becomes the read-only OEM partition holding the boot animation
sed -i 's#^\(/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/cache\) /cache .*#\1 /oem ext4 ro,context=u:object_r:oemfs:s0 wait,nofail#' $F
chcon $LABEL $F; chmod 644 $F
rm -f /vendor/etc/init/zz_oem_bootanim.rc /vendor/media/oem.img
rmdir /vendor/media 2>/dev/null
sync
mount -o remount,ro /vendor
echo "fstab now:"; grep "by-name/cache" $F
ls -lZ $F
ls /vendor/etc/init/ | grep zz_ || echo "rc removed"
chmod 644 /data/local/tmp/oem.img
