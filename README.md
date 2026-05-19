# Anonymous Pager

Android-first MVP for a privacy-focused pager app: no names, no phone numbers, no contact book. Users either generate a temporary PIN/QR to be ringed or redeem someone else's code to create a saved anonymous connection and send a ring.

The current development build uses **Firestore-only mode** so it can run on the Firebase Spark plan without Blaze. Rings are live Firestore events, so the receiver must have the app open to be informed.

## Structure

- `app/` - native Android app built with Kotlin and Jetpack Compose.
- `functions/` - Firebase Cloud Functions for anonymous pairing, ringing, blocking, and rate limits.
- `firebase.json` - Firebase emulator/deploy configuration.
- `firestore.rules` - locked-down rules; clients use callable functions for sensitive writes.

## Firebase Setup

Current project: `alghillani`.

Completed:

- Android app registered in Firebase as `com.example.anonymouspager`.
- `app/google-services.json` downloaded.
- Firestore database created.
- Firestore rules deployed.

Current Spark-plan behavior:

- Temporary PIN/QR code generation uses Firestore directly.
- Pairing and saved connections use Firestore directly.
- Ringing creates Firestore ring events.
- Receivers are informed while the app is open and listening.
- Incoming rings play an in-app pager tone and vibration.
- Super Ring uses the alarm audio stream, temporarily raises alarm volume, repeats longer, and vibrates harder.
- Blocked connections are clearly labeled and can be unblocked.
- QR codes use the pager LCD color style.

Not included without Blaze or another trusted backend:

- Closed-app push notification delivery.
- Server-enforced rate limits.
- Strong user/device ownership enforcement.
- Guaranteed bypass of silent mode, Do Not Disturb, or manufacturer battery/audio restrictions. Super Ring is best-effort while the receiver app is open.

Only required later for full push behavior:

1. Upgrade the project to the **Blaze** plan. This is currently blocking Cloud Functions and programmatic Auth initialization.
2. Enable **Firebase Authentication** and turn on **Anonymous** sign-in.
3. Deploy functions from `functions/` after the Blaze upgrade.

## Android Setup

Open this folder in Android Studio. Let it sync Gradle, then run the `app` configuration on a device or emulator.

The app requests camera permission only when scanning a QR code. Notifications use Firebase Cloud Messaging; on Android 13+ the app asks for notification permission.

## Functions

```powershell
cd functions
npm install
npm test
npm run build
firebase emulators:start
```

Deploy:

```powershell
firebase deploy --only functions,firestore
```
