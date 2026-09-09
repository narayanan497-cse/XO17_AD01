# PrivacyShield — Shoulder Surfing Protection

## What this project demonstrates

PrivacyShield uses the smartphone's front camera and on-device ML Kit Face Detection to detect an additional attentive face near the user. It then immediately hides/blur-protects sensitive content in the demo vault. An optional AccessibilityService can also place a full-screen privacy replacement over another foreground app.

## Core design

- CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`
- ML Kit Face Detection with FAST mode, classification, minimum face size, and tracking
- Primary user estimated from the largest/closest tracked face
- Shoulder surfer candidate requires an additional sufficiently large face facing the device, with eye-state support when available
- Three consecutive threat frames before activation
- Six clear frames to clear after absence; avoids flicker
- `FLAG_SECURE` on the demo vault to reduce screenshot/screen-recording exposure
- No captured frame is written to storage or uploaded
- Foreground camera service for sustained global detection
- Optional AccessibilityService overlay using `TYPE_ACCESSIBILITY_OVERLAY` for cross-app replacement shielding

## Build

Open this directory in Android Studio. The project is configured for Android Gradle Plugin 9.2.0 / Gradle 9.4.1, Java 17, compileSdk 36.

Enable Camera permission on the device. For global mode, also enable PrivacyShield under Android Settings > Accessibility.

Run `app` on a real Android phone with a front camera. Do not rely on an emulator for the final hackathon demonstration.

## Judge demo flow

1. Launch PrivacyShield.
2. Tap START PROTECTION.
3. Tap OPEN PROTECTED DEMO.
4. Keep your face close to the phone; wait for `ARMED — SINGLE VIEWER`.
5. Ask a teammate to stand behind/next to you and look toward the phone.
6. Within a few frames the vault blurs and the SCREEN PROTECTED overlay appears.
7. Teammate looks away / leaves frame; the vault restores automatically.
8. Optionally enable Global Shield accessibility permission and repeat while another app is foreground.

## Presentation claim to use

"We do not try to recognize who the person is. We estimate whether an additional person is both present and plausibly looking at the screen. That privacy-preserving design is faster, lighter, and avoids collecting biometric identity data."

