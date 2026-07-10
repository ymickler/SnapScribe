# SnapScribe 🎙️✨

SnapScribe is a completely free, 100% offline, and secure local audio transcriber for WhatsApp voice messages (and other audio files). 

Designed to combine ultimate privacy with seamless integration, SnapScribe lets you read voice messages instantly without playing them out loud.

---

## 🌟 Key Features

* **100% Offline Transcription:** Voice message transcription runs fully locally on your Android device. Your private messages never touch any cloud server, preserving your absolute privacy.
* **Flexible Display Modes (NEW!):** 
  * *Elegant Floating Overlay:* Display transcription results instantly in a sleek card over your active app (e.g., WhatsApp).
  * *Rich System Notification:* Receive a standard system notification containing the complete text, featuring quick actions to **Copy** or **Share** with a single tap.
* **Preview Sandbox (NEW!):** A built-in simulator card visible exclusively in debug/preview builds to simulate shared WhatsApp audio messages, allowing you to test both display modes and copy flows directly in the AI Studio emulator.
* **Seamless WhatsApp Integration:** Share any received voice message or audio file directly from WhatsApp to SnapScribe and view the transcribed text instantly in an elegant floating overlay.
* **Secure Local History:** Keep track of your past transcriptions in a central, searchable history page.
* **AES-256 GCM Database Encryption:** Optionally secure your locally stored transcription history. Transcribed texts are encrypted using secure cryptographic keys handled by the hardware-backed Android Keystore system.
* **Fully Dynamic UI Language Support:** Toggle between English, German, or your device's System Language dynamically without needing to restart the app.
* **Modern Material 3 Design:** A gorgeous, dark-themed user interface utilizing generous negative spacing, custom visual elements, and fully responsive layouts.

---

## 🛠️ Tech Stack & Architecture

* **Framework:** Jetpack Compose (Kotlin) with strict Material Design 3 guidelines.
* **Database:** Room SQLite database for structured storage.
* **Security:** AES-256 GCM encryption via the Android Keystore.
* **Lifecycle:** Architecture-aware Compose services (`TranscriptionOverlayService`) with manual state-driven overlays.
* **Build System:** Gradle (Kotlin DSL).
* **AI-Optimized Context:** Includes a comprehensive English [AI_CONTEXT.md](./AI_CONTEXT.md) documentation file detailing internal data flows, service endpoints, and modular simulation layers to facilitate instant context-loading for downstream AI agents.

---

## ⚠️ Requirements & Permissions

* **System Overlay Permission (Draw over other apps):** Essential to show the transcribed text instantly on top of WhatsApp when sharing an audio file. The app features a dynamic permission banner that instantly disappears once granted.
* **No Internet Permission Required:** To ensure 100% privacy, the application does not have or request Android internet access, meaning your data stays completely on your phone.

---

## 📜 Licensing

This project is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0)**.

### Under this license:
* **Share:** You are free to copy and redistribute the material in any medium or format.
* **Adapt:** You are free to remix, transform, and build upon the material.
* **Attribution:** You must give appropriate credit and provide a link to the license.
* **Non-Commercial:** You **MAY NOT** use the material for commercial purposes of any kind.

To view a copy of this license, visit [Creative Commons CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/).

---

*Note: This project is an independent utility and is not affiliated with, authorized, or endorsed by WhatsApp Inc. or Meta Platforms, Inc.*
