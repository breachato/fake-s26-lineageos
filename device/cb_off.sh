P=com.android.cellbroadcastreceiver.module
F=/data/user_de/0/$P/shared_prefs/${P}_preferences.xml
am force-stop $P
OWN=$(stat -c %u:%g $F); CTX=$(ls -Z $F | awk '{print $1}')
cp $F /data/local/tmp/cb_prefs.orig.xml
# every category the Settings screen lets the user switch off; the national-level
# (presidential) alert stays as Android keeps it mandatory
for k in enable_alerts_master_toggle enable_cmas_extreme_threat_alerts enable_cmas_severe_threat_alerts \
         enable_cmas_amber_alerts enable_public_safety_messages enable_emergency_alerts \
         enable_area_update_info_alerts enable_test_alerts enable_state_local_test_alerts; do
    sed -i "s#<boolean name=\"$k\" value=\"true\" />#<boolean name=\"$k\" value=\"false\" />#" $F
done
chown $OWN $F; chcon $CTX $F; chmod 660 $F
grep -E 'enable_' $F
# re-push the channel configuration to the modem
am startservice -n $P/com.android.cellbroadcastreceiver.CellBroadcastConfigService \
    -a com.android.cellbroadcastreceiver.intent.START_CONFIG 2>&1 | tail -1
