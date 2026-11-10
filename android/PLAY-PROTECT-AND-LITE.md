# Google Play Protect, screen control and Jarvis Lite (2026-11-10)

## Why Play Protect blocked installing Jarvis 0.1.14

Google documents the exact warning shown on Android: **"This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud."** For APKs downloaded using browser/messaging/file manager ("Internet sideload"), its installer may automatically block apps that **declare AccessibilityService**, regardless of the fact that the app requires user opt-in.

Source: [Google Play Protect warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

The full Jarvis QA 0.1.14 included an Android `BIND_ACCESSIBILITY_SERVICE` service for explicit screen navigation, so the warning is consistent with this protection. A pinned APK signing key **does not** bypass this device risk check.

**Do not tell the user to disable Play Protect, bypass the security warning, use ADB to circumvent it or install from a suspicious mirror.**

## Lite build for safely testing the other features

A separate **0.1.14-lite signed QA APK**, built from Gradle's `lite` build type, contains:

- Original `com.andriy.jarvis.stable` app ID, original stable signing certificate; intended to update earlier stable versions
- Voice command "Скажи [фраза]", timer, offline voice pack downloader, Dmytro + Lada model after manual one-time HTTPS install
- `android/app/src/lite/AndroidManifest.xml` explicitly removes the AccessibilityService from the **merged packaged manifest**
- A `screen_control_available=false` resource flag disables both UI consent launching and screen navigation commands, and transparently labels the edition as **Lite**
- No screen reading, tapping, scrolling across apps in this edition

The CI job [build-signed-lite-qa-apk.yml](../.github/workflows/build-signed-lite-qa-apk.yml) assembles and signs this variant and fails if the merged APK manifest still declares an AccessibilityService, if the APK has the wrong package ID, versionCode or original signing certificate.

**Android Play Protect is the final authority.** Removing the permission is the correct functional reduction; it is *not* a promise that Google Play Protect will always accept an APK.

## Preserving full screen control appropriately

For the full Android assistant, retain the current user-consented implementation in the source and pursue a legitimate app distribution/security review path:

1. Complete Google Play Console declarations for Accessibility API use and prominent in-app disclosure and affirmative consent.
2. Verify the feature meets [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491): no autonomous open-ended AI planning or execution, no credential exfiltration, no hidden actions, narrow scope.
3. Submit the app through Play Console testing, if accepted, and/or appeal an incorrect Play Protect classification using Google's developer appeals process.
4. Maintain the original stable certificate, app ID and upgrade path; avoid separate conflicting installs.

**Never falsely claim `isAccessibilityTool=true`**: that flag is reserved for genuine assistive tools for users with disabilities.

## Security note

The stable `.jks` keystore and any GitHub secrets export are sensitive. Do not upload or forward them through public chats, share links or repositories. Keep them in encrypted private storage and use GitHub Secrets for CI. If a signing key is compromised, rotate credentials and plan a controlled migration; changing signing certificates abruptly will prevent seamless updates.
