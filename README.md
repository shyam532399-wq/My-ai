# DEFULTER AI

An Android-native voice and chat assistant foundation built with Kotlin and Jetpack Compose.

## Current features
- Gemini chat using the Gemini 2.5 Flash REST endpoint.
- Hindi speech input through Android speech recognition.
- Spoken assistant replies through Android TextToSpeech.
- Settings screen for the user's Gemini API key.
- Approval card before the supported app-launch action.
- GitHub Actions workflow that builds and uploads a debug APK.

## Build
The project uses JDK 17, Gradle 8.7, and Android SDK 35.

GitHub Actions builds on pushes to `main` and on manual workflow dispatch. Download the APK from the workflow run's Artifacts section after a successful build.

## API key note
The key is stored in this app's local preferences for this initial build. It is not protected by a backend proxy and should not be treated as production-grade secret storage. Do not commit API keys or share APKs containing your key.

## Automation scope
The first implementation supports a user-approved app launch request (for example, `open YouTube`). It does not run arbitrary phone commands, send messages, make payments, delete files, or bypass Android permissions. Additional actions should be added one at a time with explicit approval and Android's normal permission model.

## Voice behavior
Voice input uses Android's installed speech-recognition provider; spoken replies use the device's TextToSpeech engine. Availability and language quality depend on the device's installed services.
