# DKC1Recomp Mobile — Android 0.3.0-dev

Native ARM64 port of this repository's pinned game/runtime, with an Android
SDL2 frontend. No Windows compatibility layer and no ROM bundled in the APK.
This is a development build, not a claim of full desktop feature parity.

## Uso

Instala la aplicación **DKC1Recomp Mobile**. En `Juego`, selecciona tu ROM
DKC1 USA v1.0 una sola vez. El importador comprueba el tamaño y SHA-256 y
copia el contenido al almacenamiento privado. Después basta con `Jugar` o
`Continuar partida`; no depende de que el archivo original conserve su nombre,
ubicación o permiso SAF. Cambiar la ROM es una acción explícita en `Extras`.
Desinstalar/borrar datos sí elimina la copia privada.

El panel oscuro toma como referencia la organización de PC y la adapta al
control táctil. `II`, Atrás, Guide o Start + Select abre la pausa.

- **Juego:** continuar, guardar/cargar la ranura activa, volver al inicio.
- **Gráficos:** 4:3, 16:10, 16:9 y 21:9 experimental; Nearest/Bilinear;
  los mismos modelos de color Raw/CRT/Composite/Trinitron que PC;
  scanlines ligeras y políticas de borde del nivel. El formato y los
  arreglos acuáticos opcionales se aplican en la próxima sesión.
- **Audio:** volumen y silencio persistentes.
- **Controles:** ABXY de igual radio en un rombo simétrico; cruceta continua;
  botones auxiliares del mismo tamaño. Escala 70–130 %, opacidad 20–90 %,
  editor por arrastre con Guardar/Cancelar/Restablecer y posiciones relativas
  al área segura. Dos mandos, zona muerta e intercambio de AB/XY.
- **Partidas:** cinco ranuras por formato, SRAM independiente, estado automático
  separado al pausar/salir y cada 1800 fotogramas, más exportación/restauración
  acotada de partidas y ajustes desde el inicio. La copia no incluye ROMs.
- **Extras:** cambio de ROM, diagnóstico y Baby Kong opcional del proyecto PC
  (requiere ROM de DKC3 compatible; no se probó aquí por falta de esa ROM).

El CRT complejo de escritorio, Reconstruct, MSU-1 y rebobinado **no están
integrados** en esta versión. No hay controles ficticios para esas funciones.
El ultrawide hereda el código experimental de v0.2, sin ampliar de nuevo las
colisiones ni activar el ensanchamiento experimental del cartucho.

## Identidad y actualizaciones

La clave privada de v0.2 no estaba disponible en esta sesión. Por eso v0.3
usa un paquete separado y estable: `com.ylports.dkc1recomp.mobile`.
Puede coexistir con las versiones anteriores; no requiere desinstalarlas.
Sus archivos privados no se comparten, así que esta instalación requiere
seleccionar la ROM una vez. No se afirma que pueda actualizar v0.2 en sitio.

Para las futuras actualizaciones de Mobile se deben conservar ese identificador
y **la misma clave de firma**, incrementando versionCode. La copia privada de la
clave se entrega al propietario por separado. Nunca se debe subir al repositorio.

## Build

Requiere Python 3.10+, Git, JDK 17+, SDK platform 35, Build Tools 35.0.0,
NDK 28.2.13676358, CMake 3.22.1 y Gradle 8.11.1 (bootstrap con hash verificado).
El código Java usa nivel 17. Esta compilación se ejecutó con JDK 21.

```sh
git clone --branch android/initial-port --recurse-submodules https://github.com/YlPorts/DKC1Recomp-ANdroid.git
cd DKC1Recomp-ANdroid
python android/tools/build_android.py --rom /ruta/privada/dkc1.sfc --sdk /ruta/android-sdk
```

El script valida primero la ROM y fija las revisiones de snesrecomp y SDL2.
Genera las unidades C con el generador original y un manifiesto de integridad
privado. La ROM y `generated/` deben quedar fuera de Git. El resultado por defecto
es un APK **debug**; no está firmado con la clave de la entrega Mobile.

Para release, tras la preparación:

```sh
cd android
./gradlew --no-daemon :app:assembleRelease :app:lintRelease
# Firmar el APK release con la clave privada conservada y apksigner.
python tools/verify_apk.py --apk /ruta/firmado.apk --sdk /ruta/android-sdk
```

No se deben autoaceptar licencias nuevas del SDK sin revisarlas.

## Pruebas y límites

```sh
python android/tools/run_tests.py --report /ruta/pruebas.json
```

Las pruebas de fuente cubren utilidades C con ASan/UBSan, geometría uniforme,
multitouch, rechazo de ROM inválida, exportación/restauración sin ROM, límites
y rechazo de rutas peligrosas. No equivalen a ejecutar el APK en Android.
`lint.xml` documenta excepciones acotadas al SDL2 fijado para APIs opcionales
no usadas y subclases protegidas por nivel de API. La actividad corrige el
registro privado del receptor USB para Android 13+ sin editar SDL2.

El APK v0.3 se compiló y verificó, pero **todavía no se probó en un teléfono**.
Consulta `VALIDACION_ACTUAL.md` para separar los resultados reales de lo pendiente.
