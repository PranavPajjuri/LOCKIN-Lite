# LOCKIN Lite (Android)

A minimal Android app-blocking prototype for personal focus enforcement.

## Features

- **Six-hour daily limit** for non-exempt launchable apps, measured from local midnight (`00:00:00.000`).
- **Real-time & periodic enforcement:** Checks foreground app usage both on window transitions and every 5 seconds while the screen is interactive, blocking apps mid-session when the 6-hour allowance or a 10-minute override expires.
- **Automatic system exemptions:** Active Home Launcher, Android Settings, System UI, and Emergency/Phone dialer packages are always exempt so the device cannot be locked out of core navigation or emergency calls.
- **Default user exemptions:** Phone, Messages, Contacts, WhatsApp, and ChatGPT are preselected by default, with checkboxes to add or remove user apps.
- **Timed override:** After hitting the 6-hour limit, waiting 60 seconds grants a 10-minute temporary unlock for that app.
- **Hard block:** Netflix and Hotstar (`com.netflix.mediaclient`, `in.startv.hotstar`, `in.hotstar.dplus`, `com.hotstar`, or matching app labels) remain strictly blocked until **December 2, 2026 at 00:00 IST (`Asia/Kolkata`)** with no override option.
- **Privacy by Design:** Zero internet permissions (`android.permission.INTERNET` is not requested). No data ever leaves the device.

## Automated APK Build (GitHub Actions)

1. Push this project to your GitHub repository (`main` or `master` branch).
2. Open the **Actions** tab in GitHub.
3. Wait for the **Android CI** job to complete.
4. Download the **LOCKIN-Lite-release** artifact from the bottom of the workflow summary page and unzip it to get `app-release.apk`.

## Installation & Setup on Android 12–16

Because this APK is sideloaded rather than installed from Google Play, Android enforces **Restricted Settings** on Accessibility Services:

1. **Install the APK:** Transfer `app-release.apk` to your phone and tap it to install. If prompted, enable **Allow from this source** for your file manager or browser.
2. **Google Play Protect Notice:** If prompted by Play Protect ("Unknown app"), tap **More details > Install anyway**. To permanently whitelist the application, submit the signed release APK to the [Google Play Protect Appeal Form](https://support.google.com/googleplay/android-developer/contact/protectappeals).
3. **Allow Restricted Settings (Mandatory on Android 13–16):**
   - Open your phone's **Settings** app > **Apps** > **See all apps** > **LOCKIN**.
   - Tap the **three-dot menu (`⋮`)** in the top-right corner and select **Allow restricted settings** (authenticate with your fingerprint/PIN).
   - *(Note: On some OEM skins like Vivo/iQOO FuntouchOS or Xiaomi HyperOS, you must first tap "Enable App Blocking" inside LOCKIN once so Android displays the restricted setting dialog, after which the option appears in App Info.)*
4. **Grant Usage Access:**
   - Open **LOCKIN** and tap **Grant Usage Access**.
   - Select **LOCKIN** in the Usage Access list and toggle **Permit usage access** ON.
5. **Enable App Blocking (Accessibility Service):**
   - Return to **LOCKIN** and tap **Enable App Blocking**.
   - Find **LOCKIN app blocking** under Downloaded/Installed services and toggle it ON.
6. **Disable Battery Optimization (Recommended for OEMs):**
   - Go to **Settings > Apps > LOCKIN > Battery** and set it to **Unrestricted** so the OS does not kill the background accessibility service.

## Known Accuracy & Platform Limitations

- **Midnight boundary:** `UsageStatsManager.queryEvents()` only returns lifecycle events occurring at or after `00:00:00` local time. If an app was opened before midnight and kept continuously open past midnight without an activity transition, usage prior to the first post-midnight event is not counted.
- **Locked state:** Android 10+ restricts `UsageEvents` queries while the device is locked; active sessions are closed upon `SCREEN_NON_INTERACTIVE` / `KEYGUARD_SHOWN` events so screen-off time is never counted.
- **System clock:** Hard-block expiration checks `System.currentTimeMillis()` against `2026-12-02T00:00:00+05:30`; manually changing the device date bypasses time-based rules.
- **Web browsers:** Hard-blocking targets native Netflix and Hotstar apps, not browser tabs unless the browser itself is non-exempt and the 6-hour daily limit has been exceeded.
