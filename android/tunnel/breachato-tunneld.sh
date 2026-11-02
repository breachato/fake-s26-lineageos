#!/system/bin/sh
# breachato-tunneld: runs as root from init.
#  - new random Wi-Fi MAC whenever wlan0 appears and after every disconnection, so each
#    connection uses a different MAC (and so gets a different DHCP address); the MAC is
#    changed live: taking wlan0 down makes Android switch Wi-Fi off
#  - a different WireGuard server on every connection (WireGuard app, remote-control intents)
#  - commands from the Tunnel app through files in its device-protected storage
#  - all CPU cores kept online while the screen is on
APP=it.breachato.tunnel
D=/data/user_de/0/$APP/files
I=wlan0
WG=com.wireguard.android
RX="$WG/.model.TunnelManager\$IntentReceiver"
WGDIR=/data/data/$WG/files
STATE=/data/local/tmp/tunneld
FIXED=Cloudflare        # always this tunnel; empty = rotate among all of them
DROP_POLLS=4            # carrier must stay off this many polls (4 s) to count as a disconnection
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
    # fixed tunnel: bring it down and up again, which may also give a new exit address
    if [ -n "$FIXED" ]; then
        am broadcast -n "$RX" -a com.wireguard.android.action.SET_TUNNEL_DOWN --es tunnel "$FIXED" >/dev/null 2>&1
        sleep 1
        set_tunnel "$FIXED"
        return
    fi
    cur=$(current)
    list=$(tunnels | grep -vx "$cur")
    [ -z "$list" ] && list=$(tunnels)
    n=$(echo "$list" | wc -l)
    [ "$n" -gt 0 ] && set_tunnel "$(echo "$list" | sed -n "$(( RANDOM % n + 1 ))p")"
}

rotate_on() { [ -z "$FIXED" ] && [ "$(cat $STATE/rotate 2>/dev/null)" != off ]; }

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

# Link state from the carrier flag, debounced: operstate briefly leaves "up" during key
# exchanges, and changing the MAC then breaks a working connection.
# The loop reads sysfs with shell builtins (no fork) and rewrites the status file only when
# something changed: about one process per second instead of dozens.
present=0; linked=0; off=0; last=""; age=0
while true; do
    if [ -e /sys/class/net/$I ]; then
        [ $present = 0 ] && new_mac                       # Wi-Fi just switched on
        present=1
        carrier=0; read carrier < /sys/class/net/$I/carrier 2>/dev/null
        if [ "$carrier" = 1 ]; then
            off=0
            if [ $linked = 0 ]; then                      # connected
                linked=1
                if [ -n "$FIXED" ] || rotate_on; then next_tunnel; fi
            fi
        else
            off=$((off + 1))
            if [ $linked = 1 ] && [ $off -ge $DROP_POLLS ]; then   # really disconnected
                linked=0
                new_mac
            fi
        fi
        mac=""; read mac < /sys/class/net/$I/address 2>/dev/null
    else
        present=0; linked=0; off=0; mac=""
    fi
    [ -f $D/cmd ] && handle_cmd
    # screen on: keep all eight cores available. MediaTek's power HAL rewrites the hotplug
    # limits after boot and on screen events (it even sets the big cluster to 0 cores), so
    # they are re-asserted here; thermal limits are a separate knob and stay untouched.
    bl=0; read bl < /sys/class/leds/lcd-backlight/brightness 2>/dev/null
    if [ "$bl" != 0 ]; then
        hb=""; read hb < /proc/hps/num_base_perf_serv
        hl=""; read hl < /proc/hps/num_limit_power_serv
        [ "$hl" != "4 4" ] && echo "4 4" > /proc/hps/num_limit_power_serv
        [ "$hb" != "4 4" ] && echo "4 4" > /proc/hps/num_base_perf_serv
    fi
    tun=""; [ -f $STATE/tunnel ] && read tun < $STATE/tunnel
    now="$linked|$mac|$tun"
    age=$((age + 1))
    if [ "$now" != "$last" ] || [ $age -ge 30 ]; then  # changed, or a refresh every 30 s
        write_status "$([ $linked = 1 ] && echo up || echo down)"
        last=$now; age=0
    fi
    sleep 1
done
