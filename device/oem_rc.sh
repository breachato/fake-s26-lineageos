mount -o remount,rw /vendor || exit 1
cat > /vendor/etc/init/zz_oem_bootanim.rc <<'EOF'
# custom boot animation lives on the cache partition, exposed as /oem (oemfs)
on post-fs
    mount ext4 /dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/cache /oem ro context=u:object_r:oemfs:s0
EOF
chmod 644 /vendor/etc/init/zz_oem_bootanim.rc
chcon u:object_r:vendor_configs_file:s0 /vendor/etc/init/zz_oem_bootanim.rc
sync; mount -o remount,ro /vendor
cat /vendor/etc/init/zz_oem_bootanim.rc; ls -lZ /vendor/etc/init/zz_oem_bootanim.rc
