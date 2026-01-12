# RaiForm

<p align="center">
  <img src="./app/src/main/ic_launcher-playstore.png" alt="RaiForm Logo" width="180" />
</p>

<p align="center">
  <b>The modern, offline-first fitness coaching companion for Android.</b>
</p>

<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.3.0-blue?style=flat&logo=kotlin" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Compose-Material3-green?style=flat&logo=android" alt="Compose"></a>
  <a href="https://firebase.google.com/"><img src="https://img.shields.io/badge/Firebase-Firestore%20%7C%20Auth-orange?style=flat&logo=firebase" alt="Firebase"></a>
  <a href="https://developer.android.com/training/dependency-injection/hilt-android"><img src="https://img.shields.io/badge/DI-Hilt-important?style=flat" alt="Hilt"></a>
  <a href="https://developer.android.com/topic/libraries/architecture/room"><img src="https://img.shields.io/badge/DB-Room%20(SQLite)-lightgrey?style=flat&logo=sqlite" alt="Room"></a>
</p>

## 📋 Overview

RaiForm is a robust client management and workout tracking application designed for fitness coaches. It solves the chaos of spreadsheet scheduling by providing a visual week planner and seamless progress tracking.

It features a production-grade **Offline-First Architecture**. Data is stored locally in a Room Database and synchronized bi-directionally with Google Firestore using "Delta Sync" logic, ensuring bandwidth efficiency and data integrity even across reinstallations.

## ✨ Key Features

### 🧠 Smart Automation
*   **Legacy Note Import:** Intelligent Regex parser converts raw text notes (e.g., from Google Keep) into structured Client and Session data. Handles various formats (e.g., `100kg x 5`, `100 * 5 * 5`).
*   **Weekly Resets:** Automated background workers (WorkManager) handle weekly schedule resets and log history snapshots, ensuring fresh sessions for the new week without manual input.

### ☁️ Cloud & Data
*   **Bi-Directional Sync:** Full offline support. Data syncs with Firestore when connectivity is available.
*   **Delta Syncing:** Only modified data is transferred, saving battery and data usage.
*   **Soft Deletes:** Prevents "zombie data" issues by synchronizing deletions across devices.
*   **Safe Migrations:** Robust database migration strategies ensure no data loss during app updates.
*   **JSON Backup:** Export/Import entire database to local JSON files for manual backups.

### 🏋️ Active Session
*   **Resilient Timer:** Rest timer survives app backgrounding and process death.
*   **Smart Alarm:** Respects system audio focus and Do Not Disturb/Silent modes (won't ring in the gym if phone is on vibrate).
*   **Progress Tracking:** Visualize volume, reps, and track Personal Bests (PBs) over time with interactive charts.

### 📅 Scheduling
*   **Dartboard Scheduler:** A unique, custom 24-hour circular clock UI for intuitive weekly planning.
*   **Conflict Detection:** Automatically detects and resolves scheduling conflicts between clients.
*   **Home Screen Widget:** Built with **Jetpack Glance**, providing quick access to the day's schedule right from the home screen.

## 🛠️ Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3) + Edge-to-Edge
*   **Navigation:** Type-Safe Compose Navigation
*   **Dependency Injection:** Dagger Hilt
*   **Local Database:** Room (SQLite) with Migrations & Relational Integrity
*   **Remote Backend:** Firebase (Firestore, Auth, Analytics)
*   **Background Work:** WorkManager (Constraints: Network + Battery)
*   **Widgets:** Jetpack Glance
*   **Build System:** Gradle Version Catalogs (`libs.versions.toml`) + R8/ProGuard optimized

## 🏗️ Architecture

The app follows **Clean Architecture** principles with a distinct separation of concerns:

1.  **Domain Layer:** Pure Kotlin models and Use Cases (e.g., `WeeklyResetUseCase`, `ImportDataUseCase`).
2.  **Data Layer:** Repository pattern mediating between Local (Room) and Remote (Firestore) data sources. Includes logic for diffing updates and handling relational data reconstruction.
3.  **UI Layer:** MVVM pattern with `ViewModel` and Compose screens.

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug or newer (Support for Kotlin 2.3.0).
*   JDK 17.

### Firebase Setup
This project uses Firebase for Sync and Auth. To build it successfully:

1.  Create a project in the [Firebase Console](https://console.firebase.google.com/).
2.  Enable **Authentication** (Anonymous Sign-in).
3.  Enable **Firestore Database**.
4.  Add an Android App with package: `uk.co.fireburn.raiform`.
5.  Download `google-services.json`.
6.  Place the file in `app/google-services.json`.

### Building
```bash
# Clean and Build Debug APK
./gradlew clean assembleDebug
```

## 🎨 Resources & Design

The app utilizes a custom "Zeraora" inspired theme (Electric Yellow / Slate Black).

<img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="64" align="left" style="margin-right:10px" />

*Icons and resources follow standard Android adaptive icon guidelines:*
*   **AnyDPI:** `app/src/main/res/mipmap-anydpi/`
*   **Values:** `app/src/main/res/values/` (Strings, Themes)

## 📄 License

This project is licensed under the **GNU General Public License v3.0** (or later).
See the [LICENSE](LICENSE) file for details.

Created by Mike Lothian
