# Maya Android starter

This directory contains a separate Android starter archive. The existing root-level Python FastAPI app (app.py, requirements.txt, templates/, and its Python CI workflow) is intentionally untouched.

## Source
Download `Maya-Android-source.zip` and extract it to inspect the Android Gradle project. It includes the Android app source and build configuration. This is a new starter implementation, not recovered original Maya source.

## Build workflow
The `Maya Android APK` GitHub Actions workflow extracts this archive and runs `assembleDebug`. On a successful run, download the `Maya-debug-apk` artifact from Actions.

## Scope
This starter currently provides a basic Maya chat/voice interface and Gemini REST text response integration. Full phone automation, wake-word background listening, WhatsApp actions, Guardian speaker verification, and other PDF features are not yet implemented.
