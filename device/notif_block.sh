# Block notifications of the OS-level system packages (run as root).
# Kept on purpose: clock (alarms ring through a notification), messages, downloads
# and it.breachato.charge. Dialer and telecom are blocked too: there is no SIM.
PKGS="android com.android.systemui com.android.phone com.android.settings
com.google.android.dialer com.android.server.telecom com.google.android.contacts
com.google.android.setupwizard org.lineageos.setupwizard com.android.managedprovisioning
com.android.shell me.phh.treble.app com.google.android.gms com.google.android.gsf
com.android.vending com.android.dynsystem org.lineageos.updater com.android.traceur
com.android.bluetooth com.android.networkstack.inprocess com.android.networkstack
com.android.carrierconfig it.breachato.fakeloc"
ARGS=""
for p in $PKGS; do
    u=$(pm list packages -U "$p" | grep "^package:$p " | sed 's/.*uid://')
    [ -n "$u" ] && ARGS="$ARGS $p:$u"
done
CLASSPATH=/data/local/tmp/notifctl.dex app_process / NotifCtl ${1:-block} $ARGS
