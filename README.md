# Vitre Downloader (Pure Native Android)

**Vitre Downloader** is a modern, local-first Android media downloader built 100% natively with **Kotlin** and **Jetpack Compose**. It features an OLED-optimized **Liquid Glass** aesthetic (leveraging Android 13+ AGSL GPU shaders with a graceful Material 3 fallback on earlier devices), on-device media inspection and downloading via **yt-dlp**, hardware-accelerated stream multiplexing, trimming, and audio extraction via Android's native **MediaCodec** and **MediaMuxer**, and zero dependence on external app servers or third-party backends.

---

## Technical Stack & Architecture

| Component | Implementation |
|---|---|
| **Platform** | Native Android (Min SDK 26 / Android 8.0, Target SDK 35 / Android 15) |
| **Language** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Liquid Glass UI** | AGSL GPU RuntimeShader (API 33+) with Material 3 frosted glass fallback |
| **Downloader Engine** | `yt-dlp` embedded natively via Chaquopy Python 3.11 runtime |
| **Media Processing** | Android Hardware-Accelerated MediaCodec, MediaExtractor & MediaMuxer |
| **Storage & File Access** | Android MediaStore (`IS_PENDING` on API 29+) & Storage Access Framework (SAF) |
| **Local Database** | Room (SQLite) for downloads library and inspection history |
| **Preferences** | Jetpack DataStore |
| **Background Downloads** | Android Foreground Service with ongoing notification & cancellation |
| **Build System** | Gradle 8.10.2 + AGP 8.7.3 + GitHub Actions CI/CD |

---

## Key Features

1. **Local-First & Zero Backend**:
   - Downloads, inspection history, database records, and user preferences are stored exclusively on your device.
   - No analytics servers, telemetry, or proxy servers.

2. **Accurate Format Inspection**:
   - Inspects supported media links (YouTube, Instagram, TikTok, X/Twitter, Reddit, Vimeo, SoundCloud).
   - Shows detected resolutions, audio containers, and estimated file sizes directly from the stream metadata.
   - **Never displays fabricated or non-existent formats**.

3. **In-APK Execution**:
   - The Python runtime (`yt-dlp`) is bundled directly inside the APK via Chaquopy.
   - Stream synchronization, audio extraction, and trimming are executed on-device using Android's native MediaCodec hardware pipeline.
   - Operates in-process via JNI, avoiding Android 10+ `W^X` memory violations.

4. **Liquid Glass Design System**:
   - Black and charcoal OLED surfaces (`#05070A`, `#0D111A`).
   - AGSL GPU shaders deliver live refraction, specular glare, and chromatic aberration on Android 13+ (API 33+).
   - Seamless fallback to Material 3 elevation and frosted gradients on Android 8–12.

5. **Strict Cookie & Credential Security**:
   - Dedicated **Authentication & Cookies** screen.
   - Allows users to import an intentionally exported `cookies.txt` (Netscape HTTP Cookie format).
   - Validates syntax strictly on-device before use.
   - **Never prints cookie values to logs** and never uploads them.
   - Single-tap cookie removal.

6. **External Share Integration**:
   - Supports Android `ACTION_SEND` intents: share a link directly from YouTube, Twitter, or your mobile browser into Vitre.

---

## Building the APK via GitHub Actions

This repository includes a fully automated GitHub Actions CI workflow in `.github/workflows/build-apk.yml`.

To build the APK:
1. Push this repository to GitHub:
   ```bash
   git add .
   git commit -m "feat: complete native Android Vitre Downloader implementation"
   git remote add origin https://github.com/<your-username>/vitre-downloader.git
   git push -u origin main
   ```
2. Navigate to the **Actions** tab on GitHub.
3. The **Build Vitre Downloader APK** workflow will execute on Ubuntu runners with pre-installed Android SDK and JDK 21.
4. Download the generated `vitre-downloader-debug.apk` directly from the workflow artifacts.

---

## Local Development & Gradle Build

If building on an environment with the Android SDK installed:

```bash
# Build Debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

---

## License

This project is licensed under the MIT License.
