# 🛡️ PrivacyShield — Shoulder Surfing Protection System

> **Detect the threat, not just the face.**

PrivacyShield is an Android application designed to reduce **shoulder-surfing risks** when users view sensitive information on their phones in public or shared environments.

Instead of treating every additional face as a threat, the system analyzes the surrounding situation using **position, distance, viewing direction, and duration** before activating privacy protection.

---

## 🎯 Problem

Shoulder surfing occurs when someone nearby observes sensitive information on an unlocked smartphone.

A simple approach such as:

```text
Another face detected → Protect
```

can cause false alarms.

A person may simply be:

* Walking past
* In the background
* Sitting nearby
* Looking somewhere else

The main challenge is to distinguish **normal human presence** from a potential shoulder-surfing situation.

---

## 💡 Solution

PrivacyShield uses the smartphone's **front camera** and on-device face analysis to evaluate possible threats.

The detection flow is:

```text
Camera
   ↓
Face Detection
   ↓
Context Analysis
   ↓
Threat Assessment
   ↓
Privacy Protection
```

The system considers:

* **Position**
* **Distance / apparent face size**
* **Viewing Direction**
* **Duration / Persistence**

A second face alone does **not** automatically trigger protection.

---

## 🚨 False-Detection Handling

### Normal Situation

```text
Face detected
   ↓
Brief / irrelevant presence
   ↓
Ignore
```

### Potential Shoulder Surfer

```text
Face detected
   ↓
Relevant position
   +
Close distance
   +
Facing device
   +
Sufficient duration
   ↓
Threat Confirmed
   ↓
Privacy Protection
```

This reduces unnecessary privacy interruptions caused by people who are simply nearby.

---

## 🏗️ System Architecture

```text
┌─────────────────┐
│   Front Camera  │
└────────┬────────┘
         ↓
┌─────────────────┐
│ Face Detection  │
└────────┬────────┘
         ↓
┌────────────────────────┐
│    Context Analysis    │
│                        │
│ • Position             │
│ • Distance             │
│ • Viewing Direction    │
│ • Duration             │
└────────┬───────────────┘
         ↓
┌─────────────────────┐
│  Threat Assessment  │
└────────┬────────────┘
         ↓
┌─────────────────────┐
│ Privacy Protection  │
└─────────────────────┘
```

---

## 🔐 Privacy, Features & Protection

Privacy is part of the system design itself.

### Key Features

* 🎯 Context-aware shoulder-surfing detection
* 📍 Position analysis
* 📏 Distance analysis
* 👁️ Viewing-direction analysis
* ⏱️ Duration verification
* 🚫 False-positive reduction
* ⚡ Real-time detection
* 📱 On-device processing
* 🔒 Automatic privacy protection
* 📷 Privacy-conscious camera usage
* 📦 Lightweight Android implementation

The application is intended to:

* Process camera information for real-time detection
* Keep the core analysis on the device
* Avoid permanently storing captured photos or videos
* Avoid sending camera frames to a remote server for the core detection workflow
* Minimize unnecessary data retention

> **The camera is used to protect privacy, not as a recording system.**

---

## 🚨 Privacy Activation

Privacy protection is activated **only after a potential shoulder-surfing situation has been confirmed**.

The complete flow is:

```text
Additional Face Detected
          ↓
Candidate Analysis
          ↓
Position Check
          ↓
Distance Check
          ↓
Viewing Direction Check
          ↓
Duration / Persistence Check
          ↓
Threat Confirmed
          ↓
🔒 PRIVACY PROTECTION ACTIVATED
```

### Threat Confirmation

The system does not rely on a single camera frame.

The candidate is evaluated using:

* Relative position
* Apparent distance / face size
* Viewing direction
* Persistence of the situation

Repeated observations help prevent a short-lived detection from immediately changing the privacy state.

---

### 🔒 Protection Mechanism

Once the threat state is confirmed, the application activates the configured privacy-protection mechanism.

The project supports the following protection approaches, depending on the enabled implementation:

#### Secure Screen Protection

The application can use Android's:

```text
FLAG_SECURE
```

to mark protected window content as secure.

This helps prevent the protected application's content from being captured by screenshots and displayed on non-secure outputs.

#### Privacy Overlay

The project can also use an optional:

```text
AccessibilityService
```

to place a full-screen privacy replacement/overlay over the visible content.

The simplified flow is:

```text
Threat Confirmed
      ↓
Privacy Overlay Activated
      ↓
Sensitive Screen Content Obscured
```

This is the mechanism intended to provide **visual protection from a nearby observer**.

`FLAG_SECURE` and the privacy overlay serve different purposes: `FLAG_SECURE` protects the window from screen capture, while the overlay can visually cover the content on the device.

---

### 🔄 Threat Clear & Recovery

When the potential observer leaves or the relevant conditions are no longer satisfied, the application evaluates the clear state.

```text
NORMAL
   ↓
Potential Threat
   ↓
THREAT CONFIRMED
   ↓
🔒 PROTECTION ACTIVE
   ↓
Threat Conditions Clear
   ↓
Clear-State Verification
   ↓
NORMAL
```

The implementation uses persistence in the threat/clear state to reduce rapid switching caused by temporary detection changes.

> **The goal is to protect the screen when necessary without permanently interrupting normal phone usage.**

---

## ⚙️ Technology Stack

| Technology                | Purpose                    |
| ------------------------- | -------------------------- |
| **Java**                  | Application logic          |
| **Android**               | Mobile platform            |
| **Android Studio**        | Development                |
| **CameraX**               | Camera and image analysis  |
| **ML Kit Face Detection** | Face detection             |
| **Gradle**                | Build system               |
| **Foreground Service**    | Camera-processing workflow |
| **AccessibilityService**  | Optional privacy overlay   |
| **`FLAG_SECURE`**         | Screen-content protection  |

---

## 📦 Application Size

The project targets a lightweight application footprint of:

```text
< 25 MB
```

> Verify the final APK size before presenting this as a measured result.

The actual application size depends on dependencies, resources, build configuration, and the final release build.

---

## 🧪 Testing

The system should be tested using realistic situations:

| Scenario                   | Expected Result |
| -------------------------- | --------------- |
| Only user visible          | Normal          |
| Person briefly appears     | Ignore          |
| Person walks past          | Ignore          |
| Person in background       | Ignore          |
| Nearby person looking away | Ignore          |
| Close person facing device | Analyze         |
| Sustained potential threat | Protect         |
| Threat disappears          | Recover         |

The main testing objective is to determine whether the application can distinguish a **potential shoulder-surfing situation from normal presence of other people**.

---

## 🌍 Real-World Use Cases

PrivacyShield can be useful in:

* Public transport
* Cafés
* College campuses
* Offices
* Airports
* Queues and public spaces
* Shared work environments
* Banking and authentication workflows

---

## 🚀 Future Scope

* Advanced gaze and attention detection
* Improved distance estimation
* Adaptive detection thresholds
* Better low-light performance
* Battery optimization
* Improved multi-person analysis
* More intelligent privacy responses

---

## 📱 Requirements

* Android Studio
* JDK 17
* Android SDK
* Android smartphone or emulator
* Front-facing camera
* USB debugging for physical-device testing

Current project configuration:

```text
Android Gradle Plugin : 9.2.0
Gradle                : 9.4.1
Java                  : 17
compileSdk            : 36
```

---

## 🛠️ Getting Started

Clone the repository:

```bash
git clone https://github.com/narayanan497-cse/XO17_AD01.git
```

Enter the project directory:

```bash
cd XO17_AD01
```

Open the project in **Android Studio** and allow Gradle synchronization to complete.

---

## 🔨 Build the APK

### Debug

Linux / macOS:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

### Release

Linux / macOS:

```bash
./gradlew assembleRelease
```

Windows:

```powershell
.\gradlew.bat assembleRelease
```

A release build should be properly signed before distribution.

---

## 📲 Install with ADB

Check the connected device:

```bash
adb devices
```

Install the debug APK:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

On Windows, ADB is commonly located at:

```text
%LOCALAPPDATA%\Android\Sdk\platform-tools
```

---

## 🧠 Why This Project Is Different

A basic face-detection system might work like this:

```text
Face Detected
      ↓
Protect Screen
```

PrivacyShield adds a context layer:

```text
Face Detected
      ↓
Position
      ↓
Distance
      ↓
Viewing Direction
      ↓
Duration
      ↓
Threat Assessment
      ↓
Protect Only When Needed
```

The core idea is:

> **We detect a potential shoulder-surfing situation, not just another face.**

---

## ⚠️ Limitations

The system may be affected by:

* Poor lighting
* Camera visibility
* Face orientation
* Multiple nearby people
* Device hardware
* Camera quality
* Processing and battery constraints

No computer-vision system can guarantee detection of every shoulder-surfing attempt.

---

## 🔒 Security & Privacy Disclaimer

PrivacyShield is an **additional privacy-protection mechanism**, not a replacement for normal device security.

Users should continue following standard security practices when handling sensitive information.

Privacy-related claims should match the final implementation, dependencies, permissions, and application configuration.

---

## 🔗 Repository

**GitHub:**
https://github.com/narayanan497-cse/XO17_AD01

---

## 👥 Project

**Project:** `XO17_AD01`
**Name:** PrivacyShield — Shoulder Surfing Protection System
**Platform:** Android
**Language:** Java


# 🛡️ Detect the threat, not just the face.
