# ReelVoice-Android Project Rules (GEMINI.md)

Directrices específicas para el desarrollo y mantenimiento de **ReelVoice-Android**.

---

## 1. Arquitectura y Componentes del Sistema
- **Accessibility Service (`ReelVoiceAccessibilityService`)**:
  - Encargado exclusivo del despacho de gestos en pantalla (`dispatchGesture`) para desplazamiento vertical y horizontal (TikTok, Reels, Shorts).
  - Manejar delays y curvas de aceleración para evitar detección de doble scroll o momentum descontrolado.
- **Motor de Voz (`VoiceRecognitionManager`)**:
  - Supresión activa de chime/beeps del sistema durante la inicialización continua.
  - Reconocimiento de Wake Words con tolerancia a fonética latina y fuzzy matching para comandos rápidos ("siguiente", "atrás", "pausa").
  - Gestión rigurosa del ciclo de vida para evitar reinicios en bucle rápido.
- **Servicio Flotante (`FloatingBubbleService`)**:
  - Control de superposición mediante `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
  - Animación de auto-dimming tras inactividad y zona de arrastre para descarte (`floating_dismiss_area.xml`).
- **Foreground Service (`ReelVoiceForegroundService`)**:
  - Debe declarar y sincronizar el tipo `FOREGROUND_SERVICE_MICROPHONE`.

---

## 2. Compilación y Versionado de Releases
- **Variables de Entorno**:
  - Compilar siempre asegurando `JAVA_HOME` apuntando al JBR de Android Studio (`C:\Program Files\Android\Android Studio\jbr`) o JDK 17.
- **Comando de Release Estándar**:
  ```cmd
  cmd /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat assembleRelease&& copy /y app\build\outputs\apk\release\app-release.apk ReelVoice-vX.Y.Z.apk"
  ```
- **Verificación**:
  - Validar tamaño y manifiesto con `aapt.exe dump badging ReelVoice-vX.Y.Z.apk`.
  - Asegurar que no se rompan permisos críticos: `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`.
