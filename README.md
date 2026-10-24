# AutoPlay

Aplicación Android personal para conectar los celulares al vehículo y reproducir música a través del equipo del auto (tablet o unidad central), sin anuncios de espera, sin registros innecesarios y con funciones extra para el modo administrador (el conductor).

## ¿Cómo funciona?

La misma app (un único APK) funciona en dos roles:

- **Auto**: actúa como servidor. Muestra un código QR, acepta conexiones, reproduce el audio recibido y administra los dispositivos conectados.
- **Celular**: actúa como cliente. Se conecta al auto, captura el audio del teléfono y lo envía para que suene en el vehículo.

El rol se elige manualmente o se detecta solo según el hardware: un equipo pequeño con SIM es un celular; una unidad central o una tablet sin SIM es el auto.

## Modos de conexión

1. **Wi-Fi por QR**: el auto muestra un QR (`autoplay://join?h=<host>&p=<puerto>&c=<código>`) y el celular lo escanea. También hay descubrimiento automático por mDNS (`_autoplay._tcp.`, puerto por defecto 47800).
2. **Bluetooth clásico**: el emparejamiento reemplaza al código de sesión.

Los celulares aprobados una vez quedan como de confianza y pueden volver a conectarse sin código. Si la conexión se cae, se reanuda sin pedir aprobación otra vez.

## Funciones

- Código de sesión de 6 dígitos y aprobación del conductor ("Permitir") para nuevos celulares.
- Lista de dispositivos por prioridad: suena el primero que esté en línea y comparta audio.
- El conductor puede silenciar un celular o bloquear el volumen (el celular solo puede pedir más).
- Transmisión de audio en PCM (48 kHz, estéreo, 16 bits) o AAC (128 kbps).
- Muestra la canción actual y permite controlar reproducción (play/pausa/siguiente) desde el auto.
- Servicios en primer plano para mantener la conexión activa, y soporte de Picture-in-Picture.

## Requisitos

- Android 8.0 (API 26) o superior.
- JDK 17 y Android SDK (compileSdk/targetSdk 37).
- Permisos que solicita la app: micrófono/captura de audio, cámara (escanear QR), Bluetooth, notificaciones y acceso a notificaciones (para leer la canción actual).

## Compilar y ejecutar

Desde la raíz del proyecto (en Windows usa `gradlew.bat`):

```bash
# Compilar el APK de depuración
./gradlew :autoapp:assembleDebug

# Instalar en un dispositivo conectado
./gradlew :autoapp:installDebug

# Ejecutar pruebas unitarias
./gradlew :autoapp:test

# Ejecutar una sola clase de pruebas
./gradlew :autoapp:test --tests "org.example.autoplay.SessionTest"
```

## Estructura del proyecto

```
autoapp/src/main/kotlin/org/example/autoplay/
├── car/       Servidor del auto: conexiones, aprobación, reproducción de audio, UI
├── phone/     Cliente del celular: conexión, captura de audio, medios, escáner QR
├── net/       Protocolo, transporte (TCP/Bluetooth), descubrimiento y códec AAC
├── session/   Código de sesión y enlace de unión (QR)
└── ui/        Componentes, tema y pantallas compartidas
```

## Tecnologías

Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines, CameraX, ZXing y JUnit 5.
