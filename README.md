# littlejlib-traynotify

On Windows 10/11, a Java tray app's notifications (`TrayIcon.displayMessage`) carry the header "Microsoft.Explorer.Notification.{guid}" and no proper icon. This library gives them the app's own name and icon.

```xml
<dependency>
    <groupId>io.github.littlejlib</groupId>
    <artifactId>littlejlib-traynotify</artifactId>
    <version>0.1</version>
</dependency>
```

```java
icon.displayMessage(title, text, TrayIcon.MessageType.NONE);
BalloonSender.name("myapp", () -> MyApp.class.getResourceAsStream("myapp-256.png"));
```

Call it after every balloon; only the first call of a run does anything. Outside Windows it does nothing.

## How it works

Shell_NotifyIcon balloons are filed by Windows under a sender of its own, `Microsoft.Explorer.Notification.{guid}`, whose guid follows the exe's path. The process's AppUserModelID does not apply to them. The sender appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Notifications\Settings` after the first balloon. Three seconds after the first balloon of a run, the library takes the sender with the newest `LastNotificationAddedTime` (and only if that is not older than our balloon), copies the icon to `~/littlejlib/traynotify/<app>.png`, and writes `DisplayName` and `IconUri` under `HKCU\Software\Classes\AppUserModelId\<sender>`. Windows uses them at once.

## Limits

- The very first balloon an exe ever shows still carries the raw header; every later one is named. Moving the exe gives it a new sender, named again on its next run.
- Choosing "the newest sender since our balloon" could pick another app's if two apps raise their first balloon in the same few seconds.
- This brands balloons only. Full Windows toasts (sender picture, reply box, buttons) need a registered AppUserModelID and a Start-menu shortcut, and are not in this library.
