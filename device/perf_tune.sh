RC=/vendor/etc/init/zz_oem_bootanim.rc
echo "=== CPU: big cluster always online (thermal limits stay in force)"
echo 4 4 > /proc/hps/num_base_perf_serv
sleep 1
echo "online: $(cat /sys/devices/system/cpu/online)  base: $(cat /proc/hps/num_base_perf_serv)"

echo "=== snappier animations"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global $k 0.5; done

echo "=== background: user apps restricted, fewer cached processes"
for p in $(pm list packages -3 | sed 's/package://'); do
    case $p in com.wireguard.android) continue ;; esac   # the tunnel engine must run in background
    am set-standby-bucket $p restricted
    cmd appops set $p RUN_ANY_IN_BACKGROUND ignore
    echo "restricted: $p"
done
device_config put activity_manager max_cached_processes 16
echo "max_cached_processes=$(device_config get activity_manager max_cached_processes)"

echo "=== tunnel daemon (low-fork version)"
mount -o remount,rw /vendor || exit 1
cp /data/local/tmp/breachato-tunneld.sh /vendor/bin/breachato-tunneld.sh
chmod 755 /vendor/bin/breachato-tunneld.sh; chcon u:object_r:vendor_file:s0 /vendor/bin/breachato-tunneld.sh
if ! grep -q num_base_perf_serv $RC; then
    L=$(ls -Z $RC | awk '{print $1}')
    printf '\n# all eight cores online once Android is up (thermal limits still apply)\non property:vold.decrypt=trigger_restart_framework\n    write /proc/hps/num_base_perf_serv "4 4"\n' >> $RC
    chcon $L $RC
fi
sync; mount -o remount,ro /vendor
setprop ctl.restart breachato_tunneld
sleep 4
a=$(awk '/processes/ {print $2}' /proc/stat); sleep 5; b=$(awk '/processes/ {print $2}' /proc/stat)
echo "new processes/s now: $(( (b - a) / 5 ))"
echo "tunnel: $(sed -n 's/^tunnel=//p' /data/user_de/0/it.breachato.tunnel/files/status) wifi=$(sed -n 's/^wifi=//p' /data/user_de/0/it.breachato.tunnel/files/status)"
