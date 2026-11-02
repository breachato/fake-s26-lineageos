P=com.android.cellbroadcastreceiver.module
F=/data/user_de/0/$P/shared_prefs/${P}_preferences.xml
am force-stop $P
OWN=$(stat -c %u:%g $F); CTX=$(ls -Z $F | awk '{print $1}')
sed -i 's#<boolean name="enable_cmas_presidential_alerts" value="true" />#<boolean name="enable_cmas_presidential_alerts" value="false" />#' $F
chown $OWN $F; chcon $CTX $F; chmod 660 $F
grep presidential $F
# the national level is forced on inside the receiver, so the receiver itself is switched off
# (undo: pm enable <package>)
for p in com.android.cellbroadcastreceiver.module com.android.cellbroadcastreceiver; do
    pm disable-user --user 0 $p
done
pm list packages -d | grep cellbroadcast
echo "--- charge notification:"; dumpsys notification --noredact | grep -c 'pkg=it.breachato.charge'
