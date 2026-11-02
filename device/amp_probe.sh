m() { tinymix "$1" "$2" >/dev/null 2>&1; }
echo "=== i2c devices / amp drivers"
for d in /sys/bus/i2c/devices/*; do n=$(cat $d/name 2>/dev/null); [ -n "$n" ] && echo "$(basename $d) $n"; done | grep -viE "^i2c-" | head -40
ls /sys/bus/platform/drivers/ | grep -iE "amp|spk|speaker|aw8|sia|fs1|tfa|pa$" | head
echo "=== dmesg amp (boot)"
dmesg | grep -iE "aw87|sia81|fs18|tfa98|ext_amp|extamp|ext_spk|Ext_Speaker|spk_amp|speaker_amp|AW87|pa_gpio|hp_amp" | head -20
dmesg -c >/dev/null 2>&1
m Ext_Speaker_Amp_Switch On; sleep 0.3; m Ext_Speaker_Amp_Switch Off
m Speaker_Amp_Switch On; sleep 0.3; m Speaker_Amp_Switch Off
echo "=== dmesg while toggling amps"
dmesg | grep -vE "Offset_Get|Afe_Anc_Get|HpSpk" | tail -25
echo "=== earpiece test (Voice amp)"
setprop ctl.stop audioserver; setprop ctl.stop vendor.audio-hal-2-0; sleep 2
m Handset_PGA_GAIN 8Db; m Voice_Amp_Switch On
tinyplay /data/local/tmp/tone.wav -D 0 -d 0 2>&1 | tail -1
m Voice_Amp_Switch Off
setprop ctl.start vendor.audio-hal-2-0; sleep 1; setprop ctl.start audioserver
