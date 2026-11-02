F=/vendor/etc/audio_device.xml; L=$(ls -Z $F | awk '{print $1}')
mount -o remount,rw /vendor && cp /data/local/tmp/audio_device.xml.orig $F && chcon $L $F && chmod 644 $F; sync; mount -o remount,ro /vendor
grep -c Speaker_Amp_Switch $F
setprop ctl.start vendor.audio-hal-2-0; sleep 2; setprop ctl.start audioserver; sleep 3
getprop init.svc.vendor.audio-hal-2-0; getprop init.svc.audioserver
