# Changelog de ReelVoice-Android
*Generado automáticamente el 2026-11-20 21:39*

## 🚀 Nuevas Características (Features)
- **321c621**: feat(v3.3.0): Smart Sleep auto-pause, Bluetooth SCO headset mic, and voice controls for volume, comments, and favorites (2026-09-12)
- **ff09f1d**: feat(v3.2.0): ultra-responsive voice capture with on-device recognition, clean lifecycle, and rich vocabulary (2026-09-12)
- **015905f**: feat(v3.1.0): Option 1 - extend Google speech recognizer silence timeouts and dictation mode to stabilize privacy indicator (2026-09-12)
- **7580dc6**: feat(v3.0.0): migrate to continuous hardware AudioRecord with Vosk offline engine (2026-09-12)
- **3a7e790**: feat(v2.9.0): fuzzy matching for wake words, dismiss [X] area, and auto-dimming bubble (2026-09-12)
- **3f26c89**: feat(v2.6.0): far-field voice recognition, speakerphone AEC mode, Bluetooth headset mic support, and Latin phonetics (2026-09-11)
- **0ef7f20**: feat: v2.3.0 customizable wake word and quick preset chips (2026-09-11)
- **72946de**: feat: v2.2.0 with Wake Word (Modo Seguro Antifalsos) and customizable toggle (2026-09-11)
- **6e352f5**: feat: v2.1.0 with robust phonetic recognition, swipe gesture tuning, and haptic feedback (2026-09-11)
- **efbfc82**: feat: release v2.0.0 with universal compatibility and package id com.reelvoice.scroll (2026-09-11)
- **660d2cf**: feat: initial commit for ReelVoice voice scrolling Android app (2026-09-11)

## 🐛 Correcciones de Errores (Fixes)
- **7ee500f**: fix(v3.4.0): remove Bluetooth SCO and Smart Sleep to allow universal mic access and fix voice detection drops (2026-09-12)
- **5fca893**: fix(v3.0.3): fix typo in BIND_ACCESSIBILITY_SERVICE permission name (2026-09-12)
- **0ad283f**: fix(v3.0.2): declare accessibilityEventTypes and service label for Android accessibility manager (2026-09-12)
- **0ca0108**: fix(v3.0.1): declare FOREGROUND_SERVICE_MICROPHONE, use direct internal model unpacker and sync foreground service state (2026-09-12)
- **93d04cb**: fix(v2.8.0): continuous muting of system and notification streams during session to suppress chime (2026-09-12)
- **2e910fb**: fix(v2.7.0): restore standard reliable SpeechRecognizer lifecycle and remove audio routing lock (2026-09-12)
- **04f4510**: fix(v2.5.0): silence continuous recognition chime beep and prevent rapid restart loops (2026-09-11)
- **21efcd3**: fix(v2.4.0): fix double scroll caused by partial/final speech events and gesture momentum (2026-09-11)
- **70bd467**: fix: add adaptive launcher icons, lower minSdk to 24, and configure signed release APK (2026-09-11)

## 🔧 Mantenimiento y Configuración (Chores)
- **ebdcbc8**: chore(rules): add project GEMINI.md, ignore built APKs, and update gradle configs (2026-11-20)

