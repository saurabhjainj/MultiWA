# Multi WA: 5 WhatsApp accounts in one Android app

Each account runs WhatsApp Web in its own isolated process and storage, so 5 numbers stay logged in side by side.
They show up as separate cards in Recents, so you can switch between them quickly.

## Get the APK
**Option A (no PC setup):** Push this folder to a GitHub repo. Then open **Actions → Build APK**, wait about 3 minutes, and download `MultiWA-apk`.
**Option B:** Open the folder in Android Studio, then choose **Build → Build APK(s)**. If Android Studio asks to create a Gradle wrapper, accept.

Install it on your phone. You need to allow "Install unknown apps" for the app you install from.

## Link each number
1. Open Multi WA and tap **WhatsApp 1**. A QR code appears.
2. On the phone that has number 1, open WhatsApp and go to **Settings → Linked devices → Link a device**. Scan the QR code.
3. Repeat for slots 2–5. Long-press a slot to rename it, for example "Sales" or "Office".

## Notes
- This app is a *linked device*. Each of the 5 numbers must already be active in the WhatsApp app on some phone. That phone doesn't need to stay online afterwards.
- WhatsApp unlinks companion devices that stay inactive for about 14 days. Open each slot now and then.
- No push notifications: WebView can't receive them. Messages load when you open a slot.
- Sending photos/files, voice notes and viewing media all work. Saving media to the gallery from inside the app is not supported.
- The layout is the desktop layout, so pinch-zoom or rotate to landscape if it feels small.
- Android 9+ required.
