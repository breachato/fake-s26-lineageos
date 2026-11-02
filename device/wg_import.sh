T=/data/local/tmp
WG=com.wireguard.android
uid=$(pm list packages -U $WG | grep "^package:$WG " | sed 's/.*uid://')
base=/data/data/$WG
am force-stop $WG
ctx=$(ls -Zd $base | awk '{print $1}')
mkdir -p $base/files/datastore
cp $T/wg/*.conf $base/files/ || exit 1
cp $T/wg/settings.preferences_pb $base/files/datastore/ || exit 1
chown -R $uid:$uid $base/files; chcon -R $ctx $base/files
chmod 700 $base/files $base/files/datastore; chmod 600 $base/files/*.conf $base/files/datastore/*
echo "imported: $(ls $base/files/*.conf | sed 's#.*/##; s#\.conf##' | tr '\n' ' ')"
rm -rf $T/wg                                    # private keys: keep them only inside WireGuard
