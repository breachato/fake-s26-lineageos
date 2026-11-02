"""Write adaptive launcher icons (vector) into each app's res/ folder."""
import os
import sys

A = sys.argv[1]   # android/ folder

ICONS = {
    # app: (background, glyph colour, glyph path in the 108x108 viewport, 72dp safe zone centred)
    "tunnel": ("#FF141416", "#FFA594FF",
               # shield with a keyhole cut out (even-odd)
               "M54,30 L74,37 L74,53 C74,66 65,75 54,80 C43,75 34,66 34,53 L34,37 Z "
               "M54,46 A5,5 0 1 0 54,56 A5,5 0 1 0 54,46 Z M52,55 L56,55 L57.5,66 L50.5,66 Z"),
    "applock": ("#FFA594FF", "#FFFFFFFF",
                # padlock: shackle ring and body, keyhole cut out
                "M42,50 L42,44 A12,12 0 0 1 66,44 L66,50 L61,50 L61,44 A7,7 0 0 0 47,44 L47,50 Z "
                "M38,50 L70,50 A3,3 0 0 1 73,53 L73,74 A3,3 0 0 1 70,77 L38,77 A3,3 0 0 1 35,74 L35,53 A3,3 0 0 1 38,50 Z "
                "M54,57 A4,4 0 1 0 54,65 A4,4 0 1 0 54,57 Z M52.3,63 L55.7,63 L56.5,70 L51.5,70 Z"),
    "fakeloc": ("#FF6EE7B7", "#FF0E0E0F",
                # map pin with a round hole
                "M54,30 C64.5,30 72,37.5 72,47.5 C72,60 54,79 54,79 C54,79 36,60 36,47.5 C36,37.5 43.5,30 54,30 Z "
                "M54,40 A7.5,7.5 0 1 0 54,55 A7.5,7.5 0 1 0 54,40 Z"),
}

for app, (bg, fg, path) in ICONS.items():
    res = os.path.join(A, app, "res")
    for d in ("drawable", "mipmap-anydpi-v26", "values"):
        os.makedirs(os.path.join(res, d), exist_ok=True)
    with open(os.path.join(res, "drawable", "ic_launcher_fg.xml"), "w", encoding="utf-8") as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{fg}" android:fillType="evenOdd"
        android:pathData="{path}" />
</vector>
''')
    with open(os.path.join(res, "values", "ic_launcher_bg.xml"), "w", encoding="utf-8") as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_bg">{bg}</color>
</resources>
''')
    with open(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
''')
    man = os.path.join(A, app, "AndroidManifest.xml")
    t = open(man, encoding="utf-8").read()
    if 'android:icon=' not in t:
        t = t.replace("<application ", '<application android:icon="@mipmap/ic_launcher" ', 1)
        open(man, "w", encoding="utf-8").write(t)
    print("icon:", app)
