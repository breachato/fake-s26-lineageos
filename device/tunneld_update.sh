WG=com.wireguard.android
RX="$WG/.model.TunnelManager\$IntentReceiver"
mount -o remount,rw /vendor || exit 1
cp /data/local/tmp/breachato-tunneld.sh /vendor/bin/breachato-tunneld.sh
chmod 755 /vendor/bin/breachato-tunneld.sh; chcon u:object_r:vendor_file:s0 /vendor/bin/breachato-tunneld.sh
sync; mount -o remount,ro /vendor
# exempt WireGuard from background-start limits (it is started by intents from the daemon)
dumpsys deviceidle whitelist +$WG
rm -f /data/local/tmp/tunneld/tunnel
setprop ctl.restart breachato_tunneld
sleep 2; echo "daemon: $(getprop init.svc.breachato_tunneld)"
# bring Cloudflare up right away
am broadcast -n "$RX" -a com.wireguard.android.action.SET_TUNNEL_UP --es tunnel Cloudflare >/dev/null 2>&1
echo Cloudflare > /data/local/tmp/tunneld/tunnel
sleep 6
echo "tun0: $(ip link show tun0 2>/dev/null | head -1 | cut -c1-60)"
logcat -d | grep -E 'WireGuard/GoBackend|Background start not allowed' | tail -4 | sed -E 's/([0-9]{1,3}\.){3}[0-9]{1,3}/<ip>/g'
echo "wifi: $(cmd wifi status 2>&1 | grep -oE 'Wifi is (dis)?connected|Wifi is not connected' | head -1)"
