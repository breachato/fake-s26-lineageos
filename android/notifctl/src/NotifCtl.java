import java.lang.reflect.Method;

/**
 * Turns an app's notifications on or off the way Settings does, including for packages
 * Settings refuses to block. Run as root through app_process:
 *
 *   CLASSPATH=notifctl.dex app_process / NotifCtl block|unblock|status pkg:uid ...
 */
public class NotifCtl {
    public static void main(String[] args) throws Exception {
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "notification");
        Object inm = Class.forName("android.app.INotificationManager$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        Method set = inm.getClass().getMethod("setNotificationsEnabledForPackage",
                String.class, int.class, boolean.class);
        Method get = inm.getClass().getMethod("areNotificationsEnabledForPackage",
                String.class, int.class);

        String mode = args[0];
        for (int i = 1; i < args.length; i++) {
            String[] p = args[i].split(":");
            int uid = Integer.parseInt(p[1]);
            if (!mode.equals("status")) set.invoke(inm, p[0], uid, mode.equals("unblock"));
            System.out.println(p[0] + " notifications=" + (((Boolean) get.invoke(inm, p[0], uid)) ? "on" : "off"));
        }
    }
}
