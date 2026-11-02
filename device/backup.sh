BN=$(dirname $(find /dev/block/platform -name logo -path "*by-name*" 2>/dev/null | head -1))
echo "by-name dir: $BN"
ls -l $BN | awk '{print $(NF-2), $NF}'
mkdir -p /data/local/tmp/bk
for p in logo boot recovery vendor lk lk2 odmdtbo; do
  [ -e $BN/$p ] && dd if=$BN/$p of=/data/local/tmp/bk/$p.img bs=1048576 2>/dev/null && echo "saved $p $(stat -c %s /data/local/tmp/bk/$p.img)"
done
cat /vendor/etc/fstab.* 2>/dev/null | grep -v "^#" | grep -v "^$"
ls /vendor/etc/fstab.* /vendor/etc/init/hw/ 2>/dev/null
grep -rlE "mnld|gnss" /vendor/etc/init/ 2>/dev/null
chmod -R 644 /data/local/tmp/bk/*; chmod 755 /data/local/tmp/bk
