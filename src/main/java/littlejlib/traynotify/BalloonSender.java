package littlejlib.traynotify;

import module java.base;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/// Gives a tray app's notifications its own name and icon on Windows, instead of the header
/// "Microsoft.Explorer.Notification.{guid}".
///
/// `TrayIcon.displayMessage` goes through Shell_NotifyIcon, and Windows files those balloons under a sender of its own whose
/// guid follows the exe's path; the process's AppUserModelID does not apply. That sender appears under
/// `HKCU\Software\Microsoft\Windows\CurrentVersion\Notifications\Settings` only after the first balloon, and takes a
/// `DisplayName` and an `IconUri` under `HKCU\Software\Classes\AppUserModelId\<sender>` at once, with no restart.
///
/// Call {@link #name} right after every `displayMessage`; the first call of a run does the work a few seconds later, the
/// rest return at once. Does nothing outside Windows.
///
/// ```java
/// icon.displayMessage(title, text, TrayIcon.MessageType.NONE);
/// BalloonSender.name("myapp", () -> MyApp.class.getResourceAsStream("myapp-256.png"));
/// ```
public final class BalloonSender {

    static final String
        SETTINGS = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings",
        SENDERS = "HKCU\\Software\\Classes\\AppUserModelId\\",
        PREFIX = "Microsoft.Explorer.Notification.";
    static final long FILETIME_EPOCH_MS = 11_644_473_600_000L;
    static final Logger LOG = System.getLogger(BalloonSender.class.getName());
    static final AtomicBoolean started = new AtomicBoolean();

    private BalloonSender() { }

    /// `png`: the icon, 256 px is right; it is copied to `~/littlejlib/traynotify/<appName>.png`, where Windows reads it.
    public static void name(String appName, Supplier<InputStream> png) {
        if (!System.getProperty("os.name", "").startsWith("Windows") || !started.compareAndSet(false, true)) return;
        var shownAt = System.currentTimeMillis();
        Thread.ofVirtual().name("balloon-sender").start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(3));
                var sender = newestSince(shownAt - 5_000);
                if (sender == null) { LOG.log(Level.WARNING, "no balloon sender registered since our first balloon"); return; }
                var icon = Path.of(System.getProperty("user.home"), "littlejlib", "traynotify", appName + ".png");
                Files.createDirectories(icon.getParent());
                try (var in = png.get()) { Files.copy(in, icon, StandardCopyOption.REPLACE_EXISTING); }
                reg(SENDERS + sender, "DisplayName", appName);
                reg(SENDERS + sender, "IconUri", icon.toString());
                LOG.log(Level.INFO, "named balloon sender " + sender + " " + appName);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "could not name the balloon sender", e);
            }
        });
    }

    /// The `Microsoft.Explorer.Notification.*` sender with the newest notification, if that is not older than `sinceMs`
    /// (so another app's sender is never taken when ours did not register).
    static String newestSince(long sinceMs) throws IOException {
        var p = new ProcessBuilder("reg", "query", SETTINGS, "/s", "/v", "LastNotificationAddedTime").redirectErrorStream(true).start();
        String key = null, newest = null;
        var best = 0L;
        for (var line : new String(p.getInputStream().readAllBytes()).lines().toList()) {
            if (line.startsWith("HKEY_")) key = line.trim();
            else if (line.contains("LastNotificationAddedTime") && key != null && key.contains(PREFIX)) {
                var t = Long.parseUnsignedLong(line.trim().split("\\s+")[2].replace("0x", ""), 16);
                if (t > best) { best = t; newest = key.substring(key.lastIndexOf('\\') + 1); }
            }
        }
        return newest != null && best / 10_000 - FILETIME_EPOCH_MS >= sinceMs ? newest : null;
    }

    static void reg(String key, String name, String value) throws IOException, InterruptedException {
        var p = new ProcessBuilder("reg", "add", key, "/v", name, "/t", "REG_SZ", "/d", value, "/f")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (p.waitFor() != 0) throw new IOException("reg add " + key + " " + name + " failed");
    }
}
