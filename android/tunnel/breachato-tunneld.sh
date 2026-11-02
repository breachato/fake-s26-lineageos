#!/system/bin/sh
# breachato-tunneld: runs as root from init.
#  - new random Wi-Fi MAC whenever wlan0 appears and after every disconnection, so each
#    connection uses a different MAC (and so gets a different DHCP address); the MAC is
#    changed live: taking wlan0 down makes Android switch Wi-Fi off
#  - a different WireGuard server on every connection (WireGuard app, remote-control intents)
#  - commands from the Tunnel app through files in its device-protected storage
APP=it.breachato.tunnel
D=/data/user_de/0/$APP/files
I=wlan0
WG=com.wireguard.android
RX="$WG/.model.TunnelManager\$IntentReceiver"
WGDIR=/data/data/$WG/files
STATE=/data/local/tmp/tunneld
mkdir -p $STATE

rand_mac() {
    # locally administered, unicast
    printf '%02x:%02x:%02x:%02x:%02x:%02x' $(( (RANDOM % 64) * 4 + 2 )) \
        $((RANDOM % 256)) $((RANDOM % 256)) $((RANDOM % 256)) $((RANDOM % 256)) $((RANDOM % 256))
}

new_mac() { ip link set dev $I address "$(rand_mac)" 2>/dev/null; }

tunnels() { ls $WGDIR/*.conf 2>/dev/null | sed 's#.*/##; s#\.conf$##'; }

current() { cat $STATE/tunnel 2>/dev/null; }

set_tunnel() {
    cur=$(current)
    [ -n "$cur" ] && [ "$cur" != "$1" ] && \
        am broadcast -n "$RX" -a com.wireguard.android.action.SET_TUNNEL_DOWN --es tunnel "$cur" >/dev/null 2>&1
    am broadcast -n "$RX" -a com.wireguard.android.action.SET_TUNNEL_UP --es tunnel "$1" >/dev/null 2>&1
    echo "$1" > $STATE/tunnel
}

next_tunnel() {
    cur=$(current)
    list=$(tunnels | grep -vx "$cur")
    [ -z "$list" ] && list=$(tunnels)
    n=$(echo "$list" | wc -l)
    [ "$n" -gt 0 ] && set_tunnel "$(echo "$list" | sed -n "$(( RANDOM % n + 1 ))p")"
}

rotate_on() { [ "$(cat $STATE/rotate 2>/dev/null)" != off ]; }

write_status() {
    [ -d $D ] || return
    uid=$(stat -c %u $D); ctx=$(ls -Zd $D | awk '{print $1}')
    {
        echo "wifi=$1"
        echo "mac=$(cat /sys/class/net/$I/address 2>/dev/null)"
        echo "ip=$(ip -4 addr show $I 2>/dev/null | grep -o 'inet [0-9.]*' | cut -d' ' -f2)"
        echo "tunnel=$(current)"
        echo "rotate=$(rotate_on && echo on || echo off)"
        echo "tunnels=$(tunnels | tr '\n' ',')"
    } > $D/status.tmp
    chown $uid:$uid $D/status.tmp; chcon $ctx $D/status.tmp; chmod 600 $D/status.tmp
    mv $D/status.tmp $D/status
}

handle_cmd() {
    [ -f $D/cmd ] || return
    c=$(cat $D/cmd); rm -f $D/cmd
    case "$c" in
        new_identity) next_tunnel; new_mac ;;     # link drops, Android reconnects with the new MAC
        next_tunnel)  next_tunnel ;;
        tunnel:*)     set_tunnel "${c#tunnel:}" ;;
        rotate:on)    echo on > $STATE/rotate ;;
        rotate:off)   echo off > $STATE/rotate ;;
    esac
}

present=0; oper=none
while true; do
    if [ -e /sys/class/net/$I ]; then
        [ $present = 0 ] && new_mac                       # Wi-Fi just switched on
        present=1
        now=$(cat /sys/class/net/$I/operstate)
        if [ "$oper" = up ] && [ "$now" != up ]; then new_mac; fi                  # disconnected
        if [ "$oper" != up ] && [ "$now" = up ] && rotate_on; then next_tunnel; fi  # connected
        oper=$now
    else
        present=0; oper=none
    fi
    handle_cmd
    write_status "$oper"
    sleep 0.5
done
