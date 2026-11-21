# Google Play Store Listing // ReelVoice-Android
**Application ID**: `com.reelvoice.scroll`  
**Versión Actual**: `v3.4.0` (versionCode `37`)  
**Generado el**: 2026-11-20 23:55  

---

## 1. Título de la Aplicación (Máximo 30 caracteres)
```
ReelVoice - Scroll por Voz
```
*(Longitud: 26 / 30 caracteres)*

---

## 2. Descripción Corta (Máximo 80 caracteres)
```
Navega y controla TikTok, Reels y Shorts con tu voz sin tocar la pantalla.
```
*(Longitud: 72 / 80 caracteres)*

---

## 3. Descripción Completa (Máximo 4000 caracteres)
```markdown
¿Cocinas, haces ejercicio, lavas los platos o tienes las manos ocupadas? Con **ReelVoice**, disfruta de tus videos favoritos en TikTok, Instagram Reels, YouTube Shorts y Facebook sin tocar la pantalla de tu teléfono.

### 🎙️ Comandos de Voz Simples y Precisos:
- **"Siguiente" / "Baja"**: Salta al siguiente video al instante.
- **"Anterior" / "Sube"**: Vuelve a ver el video previo.
- **"Dale like" / "Me gusta"**: Envía un 'me gusta' sin usar las manos.
- **"Pausa" / "Play"**: Detén o reanuda la reproducción.
- **"Más volumen" / "Menos volumen"**: Ajusta el audio de forma inteligente.

### 🛡️ Modo Seguro Antifalsos:
¿Temes que el audio del propio video active gestos por accidente? Activa el **Modo Seguro** y la app solo responderá cuando digas la palabra clave: *"Reel siguiente"* o *"Reel like"*.

### ⚡ Ligera y Eficiente:
- Procesamiento On-Device en el chip para una respuesta inmediata (< 500ms).
- Compatible con micrófonos de auriculares inalámbricos Bluetooth.
- Mínimo impacto en la batería con optimización de suspensión.

---

### 🔒 Divulgación Obligatoria de API de Accesibilidad (Google Play Compliance):
ReelVoice utiliza la API de Servicios de Accesibilidad de Android (`AccessibilityService`) exclusivamente para simular gestos táctiles de desplazamiento vertical (scroll swipe) y toques de pantalla por instrucción directa del usuario a través de su voz.

- **Finalidad**: Permitir el control manos libres del dispositivo para usuarios con movilidad reducida o manos ocupadas.
- **Privacidad Absoluta**: ReelVoice NO recopila, NO almacena, NO transmite ni comparte ningún dato personal, credenciales, audio ni contenido visual de tu pantalla. Todo el procesamiento de voz ocurre localmente en tu dispositivo.
```

---

## 4. Novedades de esta Versión (What's New)
```
* Mejoras de estabilidad y rendimiento en el reconocimiento por voz.
```

---

## 5. Justificación de Permisos para Google Play Console
* **RECORD_AUDIO**: Requerido exclusivamente para la escucha de comandos de voz mediante el motor local SpeechRecognizer.
* **POST_NOTIFICATIONS**: Notificación persistente requerida por Android para mantener activo el ForegroundService mientras navegas en otras aplicaciones.
* **SYSTEM_ALERT_WINDOW**: Burbuja flotante visual opcional que indica el estado del micrófono en pantalla.
* **BIND_ACCESSIBILITY_SERVICE**: Ejecución de gestos de desplazamiento automatizados por comando de voz.

---

## 6. Lista de Verificación de Activos Gráficos
- [ ] **Icono de la App**: 512 x 512 px (PNG de 32 bits, fondo transparente o plano, máx 1 MB).
- [ ] **Gráfico de Funciones**: 1024 x 500 px (JPG o PNG de 24 bits, sin transparencias).
- [ ] **Capturas de Teléfono**: Mínimo 2 capturas (recomendado 4 a 8) en resolución 1080 x 2400 (proporción 16:9 / 9:16).
- [ ] **Enlace a Política de Privacidad**: URL pública accesible.
