<p align="center">
  <img src="branding/icon-512.png" width="128" alt="Wen app icon">
</p>

<h1 align="center">Wen · وين</h1>

<p align="center">
  An anonymous pager for Android. Pair two phones with a PIN or QR code, then "ring" each other.<br>
  No accounts, no phone numbers, no contacts.
</p>

---

## What it does

- **Anonymous pairing**: one phone shows a short-lived PIN / QR code (valid 10 minutes), the other enters or scans it.
- **Ring**: send a ring to a paired phone. It plays on the alarm audio stream with vibration, and the receiver can accept or decline.
- **No identity**: devices sign in with Firebase Anonymous Auth. Nobody sees a name, number or contact list.
- **Block / unblock / delete** any connection.
- **Rate limit**: max 3 rings per connection every 5 minutes (enforced server-side).
- **Retro LCD look**: Jetpack Compose UI styled like a classic pager.

## How it works

```text
Android app ──(callable functions)──▶ Cloud Functions ──▶ Firestore
     ▲                                       │
     └──────────── FCM push (ring) ◀─────────┘
```

- Every write (create code, pair, ring, block…) goes through **Cloud Functions**, keyed by the anonymous auth uid.
- **Firestore rules** only let a device read its own connections and rings; clients can't write directly.
- Rings arrive as **FCM** (Firebase Cloud Messaging, Google's push service) notifications, so they work when the app is closed.

## Project layout

```text
app/            Android app (Kotlin, Jetpack Compose, CameraX + ML Kit for QR)
functions/      Firebase Cloud Functions (TypeScript, Node 22)
firestore.rules Firestore security rules
firebase.json   Firebase deploy + emulator config
branding/       App icon sources (SVG) and previews
```

## Setup

### Requirements

- Android Studio (recent) with JDK 17
- Node.js 22 and the Firebase CLI: `npm i -g firebase-tools`
- A Firebase project on the **Blaze** (pay-as-you-go) plan. Cloud Functions need it; a hobby-level usage normally stays inside the free tier.

### 1. Firebase project

1. Create a project in the [Firebase console](https://console.firebase.google.com).
2. **Authentication → Sign-in method →** enable **Anonymous**.
3. **Firestore Database →** create a database.
4. **Project settings → Add app → Android** with package name `com.anas.wen` (or your own, see below). Download `google-services.json` into `app/`.
   - This file is git-ignored on purpose. Don't commit it.

### 2. Deploy the backend

```bash
firebase login
firebase use --add            # pick your project
cd functions && npm install && cd ..
firebase deploy --only functions,firestore
```

Functions are deployed to `us-central1` (set in `functions/src/index.ts`).

### 3. Run the app

1. Open the repo root in Android Studio and let Gradle sync.
2. Run on a device or emulator with Android 8.0+ (API 26). Use two devices to test pairing and ringing.

Or from the command line: `./gradlew assembleDebug`

### Using your own package name

Change `applicationId` in `app/build.gradle.kts`, and register that same name as the Android app in Firebase before downloading `google-services.json`.

## Development

```bash
cd functions
npm test                      # unit tests (vitest)
npm run serve                 # build + local emulators (auth, functions, firestore)
```

## License

[MIT](LICENSE)
