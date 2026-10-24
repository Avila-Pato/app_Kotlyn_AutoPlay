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

## Mi auto

Apartado para llevar el seguimiento del auto. Está en las dos pantallas: en el celular, como tarjeta
**Mi auto** en la pantalla para unirse (y como botón discreto estando conectado); en la radio, como
botón **Mi auto**. Entrar ahí no cambia el rol del equipo.

- **Resumen**: kilometraje actual (**Actualizar km**) y lo más urgente, por ejemplo "Aceite en 3.662 km".
  El kilometraje no puede bajar.
- **Aceite**: km que faltan, fecha estimada del próximo cambio (según el ritmo de uso, con tope de
  12 meses), barra de progreso e historial. **Registrar cambio de aceite** propone 5W-30 y el próximo
  cambio 10.000 km después, ambos editables.
- **Papeles**: permiso de circulación, SOAP y revisión técnica con su vencimiento. Verde con más de 60 días,
  amarillo entre 15 y 60, rojo con menos de 15 o vencido. Al tocar uno se cambia la fecha (día/mes/año o mes/año).
- **Papeles propios**: **Agregar papel** crea uno nuevo (por ejemplo un seguro) con nombre, fecha y nota; al tocar
  cualquier papel se puede cambiar su fecha o borrarlo. Cada papel tiene su anillo en el resumen.
- **Historial de aceite**: al tocar un cambio se abre su ficha (solo lectura, no se edita) con la opción de eliminarlo;
  **Borrar historial** quita todos. Siempre pregunta antes de borrar.
- **Pendientes**: lista con casillas; se pueden agregar y borrar.
- **Ficha** y **Notas** del auto. En la ficha se toca un dato (nombre, patente, color…) para cambiarlo.

La pantalla muestra arriba el kilometraje y cuatro anillos (aceite, permiso, SOAP y revisión técnica) con lo que
queda de cada uno, y debajo pestañas para ver una sección a la vez: **Aceite**, **Papeles**, **Pendientes** y **Ficha**.
Tocar un anillo abre su sección. En la radio los anillos quedan a la izquierda y la sección elegida a la derecha.

Los datos quedan guardados en el equipo (`SharedPreferences`, sin dependencias nuevas) y la primera vez
se precargan con los datos del auto. El código está en `mycar/`: `MyCarStore` (guardado),
`MyCarStatus` (cálculos sin Android, con tests en `MyCarStatusTest`) y `MyCarScreen` (pantalla).

## Panel de apps y avisos de voz (en el auto)

- **Panel de apps**: el lápiz al final del panel (o de la fila de atajos en pantallas angostas) abre un
  selector con todas las apps del equipo. Marca las que quieras (Waze, Spotify, Maps, etc.); se guardan en el
  equipo y se abren con un toque. Maps y Waze siguen sumándose al botón **Navegar**.
- **Aviso de voz**: cuando un celular con el volumen bloqueado pide más, el auto lo dice una sola vez
  ("Tomás pide más volumen") por los parlantes, bajando la música un momento, aunque estés en otra app.
  Se apaga con el interruptor **Avisos de voz**. Si la radio no tiene voz en español, suena un aviso corto.

## Varios autos y datos en el celular

- Los autos viven en el **celular**. En **Mi auto** hay una fila con tus autos (el Hyundai viene de fábrica) y
  **+ Agregar otro auto**, que crea uno totalmente vacío: lo llenas desde su **Ficha**. Cambiar de auto es un toque, y
  **Borrar este auto** lo quita (siempre queda al menos uno).
- Al conectarte a una radio, el celular le manda el auto que tienes elegido y la radio lo muestra en **Mi auto**.
  Lo que cambies en la radio vuelve al celular. Con varios celulares conectados, la radio muestra el auto del primero
  de la lista (el que pone la música). Sin celular, la radio dice "Conecta tu celular para ver tu auto".
- Esto cambia el protocolo a la versión 5: **actualiza AutoPlay en el celular y en la radio a la vez**; una versión
  vieja y una nueva no se conectan.

## Papeles con imágenes y más certificados

- **Agregar papel** ofrece sugerencias (licencia de conducir, certificado de inscripción, certificado de revisión
  técnica, permiso de circulación, SOAP, seguro, homologación, padrón) o cualquier nombre. El vencimiento es opcional:
  la inscripción, por ejemplo, no vence; un papel sin fecha queda "Sin vencimiento" y no ocupa un anillo.
- Cada papel guarda hasta **6 imágenes**: **Tomar foto** o **Galería** (no pide permisos). Se reducen a 1600 px y se
  guardan en el almacenamiento privado de la app; tocar una miniatura la abre a pantalla completa con zoom, y desde ahí
  se puede eliminar (nunca editar). Las imágenes **se ven solo en el celular**: la radio muestra "2 imágenes (verlas en
  el celular)". Si desinstalas la app, las imágenes se pierden (no hay respaldo).

## Dos versiones de la app

- `personal`: arranca con los datos del Hyundai ya cargados. `./gradlew :autoapp:assemblePersonalDebug`
- `clean`: no lleva datos de nadie; arranca con un auto vacío, "Mi auto". `./gradlew :autoapp:assembleCleanDebug`

Las dos usan el mismo `applicationId`: instalar la limpia sobre la personal conserva los datos ya guardados en ese
teléfono; para ver el arranque desde cero, desinstala antes.

## Mensajes al auto y música que se cierra

- En el celular, **Mensaje al auto** deja escribir una línea (hasta 140 caracteres). La radio la muestra ("Mensaje de Ana: llegamos
  en 5 minutos") y la **lee en voz alta** (se apaga con el interruptor **Avisos de voz**). Cada celular puede mandar uno cada 5
  segundos, y el aviso se va solo a los 20 segundos o con **Listo**.
- Si se abre la **radio del equipo** (u otra app que tome el audio), AutoPlay **pausa la música** del celular y deja de sonar; con
  una pausa corta (una llamada) la retoma sola al terminar, y con una larga (la radio) queda cerrada hasta que alguien le dé play.
  Solo funciona si esa app pide el audio de forma normal en Android: si tu radio usa un circuito propio, AutoPlay no se entera.
- Esto sube el protocolo a la versión 6: actualiza AutoPlay en el celular y en la radio a la vez.
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
