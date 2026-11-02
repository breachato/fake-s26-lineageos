T=/data/local/tmp; C=/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/cache
RC=/vendor/etc/init/zz_oem_bootanim.rc
# media/  = animation before the PIN, media2/ = animation after it
chmod 755 $T/oemroot/media $T/oemroot/media2
chmod 644 $T/oemroot/media/bootanimation.zip $T/oemroot/media2/bootanimation.zip
ls -l $T/oemroot/media/ $T/oemroot/media2/
rm -f $T/oem.img; mke2fs -t ext4 -b 4096 -L oem -d $T/oemroot $T/oem.img 75000 2>&1 | tail -1
umount /oem/media 2>/dev/null   # post-PIN bind of media2, if active
umount /oem && echo unmounted
dd if=$T/oem.img of=$C bs=1048576 conv=fsync 2>&1 | tail -1
mount -t ext4 -o ro,context=u:object_r:oemfs:s0 $C /oem && ls -lR /oem/media /oem/media2
# once the PIN is accepted, bootanim (restarted with the framework) reads media2 instead
if ! grep -q media2 $RC; then
    L=$(ls -Z $RC | awk '{print $1}')
    mount -o remount,rw /vendor || exit 1
    printf '\n# after the PIN: second half of the boot animation\non property:vold.decrypt=trigger_post_fs_data\n    mount none /oem/media2 /oem/media bind\n' >> $RC
    chcon $L $RC
    sync; mount -o remount,ro /vendor
fi
echo "--- rc"; cat $RC
