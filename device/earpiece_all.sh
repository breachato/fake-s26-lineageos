F=/vendor/etc/audio_device.xml
L=$(ls -Z $F | awk '{print $1}')
cp /data/local/tmp/audio_device.xml.orig /data/local/tmp/ad.xml
# The loudspeaker on this unit is dead: every "speaker" path drives the earpiece
# (Voice amp, max handset gain) instead of the class-D / external amp.
awk '
/<path name="/ {
  p = ""
  if ($0 ~ /name="(ext_speaker_output|speaker_output|two_in_one_speaker_output)"/) p = ($0 ~ /turnon/) ? "on" : "off"
  print; next
}
/<\/path>/ { p = ""; print; next }
p != "" && /<kctl / {
  if (!done[p NR]) {
    if (p == "on" && !emitted_on[pathid]) {
      print "        <kctl name=\"Voice_Amp_Switch\" value=\"On\" />"
      print "        <kctl name=\"Handset_PGA_GAIN\" value=\"8Db\" />"
    } else if (p == "off" && !emitted_off[pathid]) {
      print "        <kctl name=\"Voice_Amp_Switch\" value=\"Off\" />"
    }
  }
  next
}
{ print }
' /data/local/tmp/audio_device.xml.orig > /data/local/tmp/ad.xml
mount -o remount,rw /vendor || exit 1
cp /data/local/tmp/ad.xml $F; chcon $L $F; chmod 644 $F
sync; mount -o remount,ro /vendor
grep -n -A3 -E 'name="(ext_speaker_output|speaker_output|two_in_one_speaker_output)"' $F
setprop ctl.restart vendor.audio-hal-2-0; sleep 2; setprop ctl.restart audioserver; sleep 4
getprop init.svc.vendor.audio-hal-2-0; getprop init.svc.audioserver
