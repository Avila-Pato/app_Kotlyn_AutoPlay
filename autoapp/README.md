# AutoPlay: la música del celular en los parlantes del auto

Una sola app con dos modos. Se instala el **mismo APK** en la radio Android del auto
y en el celular, y al abrirla se elige "Soy el Auto" o "Soy el Celular".

> Requisito: la pantalla del auto tiene que ser una radio con Android que permita
> instalar APKs. Las pantallas de fábrica de la mayoría de las marcas no lo permiten.

## Cómo funciona

- Varios celulares pueden conectarse al mismo auto (hasta 8). Cada uno entra con el código o el QR
  y el auto pulsa **Permitir**.
- **El auto decide la prioridad**: en su pantalla se ven los dispositivos conectados en orden; con las
  flechas o **Poner su música** se elige quién suena. Suena el primero de la lista que esté conectado
  y tenga el audio activado. Los celulares solo ven la lista y de quién es la música.
- El celular, al conectarse, pide **Activar audio** (Android lo muestra como "compartir pantalla";
  solo se usa el sonido). Lo que suene en YouTube, Spotify, etc. se envía al auto.
- Los dos lados siguen funcionando **en segundo plano** con una notificación fija
  ("Estás conectado a…"). Desde la notificación se puede desconectar.

```
Celular                                         Auto
┌─────────────────────────┐   Wi-Fi local    ┌──────────────────────────────┐
│ PhoneService (1er plano)│                  │ CarService (1er plano)       │
│  AudioCapture ──PCM────►│── TCP :47800 ───►│ CarServer ── AudioPlayer     │
│ PhoneClient             │◄── Roster ───────│   lista con prioridad        │
│   Hello / Resume / Ping │                  │   código 6 dígitos, Permitir │
│ CarDiscovery (NSD busca)│                  │ CarDiscovery (NSD anuncia)   │
└─────────────────────────┘                  └──────────────────────────────┘
```

- **Protocolo** (`net/Protocol.kt`): cada mensaje es un frame `[tipo 1 byte][largo 4 bytes][datos]`.
  El audio va como PCM 48 kHz estéreo de 16 bits en bloques de 20 ms (~1,5 Mbit/s, sin problema en Wi-Fi local).
- **Límites de Android**: la captura de audio necesita Android 10 o más reciente en el celular, y algunas
  apps bloquean que se capture su audio; en esas no se escuchará nada en el auto.

## Compilar sin Android Studio

Hace falta el SDK de Android (ya está en `%LOCALAPPDATA%\Android\Sdk`) y Java 17 o más reciente.

```
.\gradlew.bat :autoapp:assembleDebug
```

El APK queda en `autoapp\build\outputs\apk\debug\autoapp-debug.apk`.

## Instalar en el celular y en el auto

1. En cada equipo, activa las *Opciones de desarrollador*: en *Ajustes → Acerca del teléfono*,
   toca 7 veces *Número de compilación*. Después activa la *Depuración USB*.
2. Conecta el equipo por USB y ejecuta:
   ```
   %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r autoapp\build\outputs\apk\debug\autoapp-debug.apk
   ```
   Si la radio no tiene USB de datos, copia el APK a un pendrive y ábrelo con el
   explorador de archivos de la radio. Tendrás que permitir "instalar apps de origen desconocido".
3. Pon los dos equipos en la misma red: el hotspot del celular con la radio conectada a él, o los dos en el mismo Wi-Fi.

## Probar

1. En el auto: abre AutoPlay y pulsa **Soy el Auto**. Aparecen el código, el QR y la IP.
2. En el celular: abre AutoPlay, pulsa **Soy el Celular**, escribe el código y toca el auto en la lista.
   Si no aparece en la lista, escribe la IP que muestra el auto y pulsa **Conectar**.
3. En el auto: pulsa **Permitir**.
4. En el celular: acepta el permiso de audio y el de "compartir pantalla" (elige pantalla completa).
5. Sal de la app, abre YouTube o Spotify y dale play: debe escucharse en el auto.
6. Conecta un segundo celular con el código nuevo y, en el auto, cambia el orden para elegir quién suena.

## Problemas comunes

- **El auto no aparece en la lista:** algunos routers y hotspots bloquean el multicast (mDNS).
  Usa la conexión manual con la IP.
- **"Código incorrecto o vencido":** el código dura 5 minutos y sirve una sola vez.
  Después de 5 intentos fallidos se genera uno nuevo.
- **"No se pudo conectar":** comprueba que los dos equipos estén en la misma red y que
  no haya un "aislamiento de clientes" activado en el router.
