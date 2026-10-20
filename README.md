# Smart Launcher

A lightweight Android TV home screen built with Kotlin and Jetpack Compose for TV. The interface is designed for a D-pad remote: three continue-watching cards, a frosted favourites strip, and a horizontally scrolling A–Z app dock. A translucent top-right pill shows the live time, network state, and Settings.

## Features

- Android HOME / DEFAULT / LEANBACK_LAUNCHER activity, with a TV banner and launcher icon.
- D-pad focus styling, OK activation, explicit remote OK long-press handling, Back-to-close panels, Menu-to-open-settings, and a five-second Back hold for Safe mode.
- App discovery across both `CATEGORY_LAUNCHER` and `CATEGORY_LEANBACK_LAUNCHER`, A–Z ordering, package-change refresh, a bounded icon cache, and favourite add/remove/reorder actions.
- Three local media-history cards backed by a notification listener's MediaSession metadata. Cards remain after playback stops, can be filtered by app, and fall back to permission/help placeholders.
- Nine original gradient wallpapers, a system document-picker wallpaper option, light/dark adaptive glass tint, and a cached pre-blurred wallpaper for Android versions before 12. Android 12+ uses Compose's platform blur for the static wallpaper layer.
- Settings for icon shape/roundness/size, labels, hidden and renamed apps, custom app icons, favourite count, dock density, recent apps, media-app filtering, animations, and fonts.
- Five bundled SIL Open Font License families (Inter, Poppins, Montserrat, Playfair Display, and Lora) plus the system font.
- Versioned JSON settings import/export, reset, setup reminders, About, and conditional TV-input and assistant shortcuts.
- No broad storage permission, overlay permission, network permission, advertising SDK, or analytics SDK. The screen does not keep the TV awake, so the system screensaver remains in control.

## Install / first-run setup

1. Install `SmartLauncher-v1.0.apk` from a trusted source, or sideload it with Android Debug Bridge:

   ```sh
   adb install SmartLauncher-v1.0.apk
   ```

2. Open **Smart Launcher** and follow the setup screen. Choose Smart Launcher as the system **Home app** if you want it to start as the TV launcher and after boot.
3. To make the remote's Home key return here on devices that allow accessibility key filtering, open **Settings → Accessibility → Smart Launcher Home key shortcut** and enable it. The service requests key filtering only; it sets `canRetrieveWindowContent=false`, does not subscribe to screen events, and does not read screen text or window content.
4. For continue-watching cards, open **Settings → Notifications / Notification access → Smart Launcher media history** and allow access. The listener reads MediaSession metadata; it never reads notification text.
5. **Display over other apps is not requested or used.** The wallpaper picker uses Android's document picker and does not request broad storage access.

If a permission or default-home step is skipped, Smart Launcher stays usable and shows a plain reminder instead of crashing. Android and TV manufacturers can restrict interception of reserved keys; on those devices, choose Smart Launcher as the default Home app and the system Home action will open it normally.

## Safe mode / getting out of trouble

- On the home screen, hold **Back for five seconds**. Smart Launcher enables Safe mode and pauses its Home-key override without disabling Android's accessibility setting.
- Or open **Settings → Safe mode**. Turn it off there when you want the override active again.
- If you need to switch launchers, use the TV's system **Home app** setting or disable the Smart Launcher accessibility service. Installing another APK with the same signing key and package ID updates this app without uninstalling it.

## Remote controls

- **D-pad:** move focus. Focused controls scale slightly and receive a bright outline.
- **OK / Enter:** open or select. Hold OK on an app for its actions; hold OK on a favourite to remove or reorder it.
- **Back:** close a panel/dialog; on the home screen it does not exit. Hold it five seconds on Home for Safe mode.
- **Menu / Settings:** open the Settings panel.

The app saves the last focused item and restores it when the launcher returns. App launches fall back to the target app if the source app does not expose a deep link for a specific media item.

## Media and privacy notes

Android does not expose other apps' official **Watch Next** catalogs to third-party launchers. Smart Launcher uses the supported Notification Listener + MediaSession route: app package/name, title, subtitle, playback state, and available artwork. Recent entries and small artwork thumbnails are stored locally in this app's private storage; the home screen shows the latest three distinct permitted apps. You can filter media sources or clear the saved history in Settings. Nothing is sent over the network.

The accessibility service filters only `KEYCODE_HOME`. Android/OEM behavior varies, and some devices may not deliver the reserved Home key to accessibility services; the default-Home role is the dependable fallback.

## Screenshots

Screenshots of the built app on a 1080p and 720p Android TV emulator/device still need to be captured. The attached reference images were used only for layout inspiration and are not included or reused as Smart Launcher screenshots. Add genuine captures to `docs/screenshots/` after running the app.

## Build and test

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36, and an Android TV emulator/device for visual and D-pad verification.

```sh
./gradlew lint testDebugUnitTest assembleDebug
```

The project is one Android app module, min SDK 26, target/compile SDK 36. Release builds enable R8 and resource shrinking:

```sh
./scripts/build-release.sh
```

The build script writes a signed `SmartLauncher-v1.0.apk` in the repository root. Android lint and the local unit tests should run before release; test D-pad navigation and layout on both 1080p and 720p TVs as well.

## App identity and release key

- Stable package/application ID: `com.saintm.smartlauncher`.
- A PKCS#12 release keystore and its Gradle signing properties were created outside this repository at:
  - `~/.config/smart-launcher/SmartLauncher-release.p12`
  - `~/.config/smart-launcher/signing.properties`
- Both files are mode `0600`; the containing directory is mode `0700`. Neither belongs in Git. Back up **both** files together to at least two encrypted offline locations. Keep the password file private; never attach it to an issue, commit, or release. The signing key is required for in-place upgrades—if it is lost, Android will not accept a future APK as an update to this installation.
- The repository's release workflow expects these GitHub Actions secrets when publishing from a `v*` tag: `SMART_LAUNCHER_KEYSTORE_BASE64`, `SMART_LAUNCHER_STORE_PASSWORD`, `SMART_LAUNCHER_KEY_ALIAS`, and `SMART_LAUNCHER_KEY_PASSWORD`.

## Screenshots directory

`docs/screenshots/` is reserved for real emulator/device captures; no reference-launcher screenshots are redistributed.
