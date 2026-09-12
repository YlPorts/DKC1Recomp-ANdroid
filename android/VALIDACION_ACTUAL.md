# Validación Android v0.1.0-dev — 12 de septiembre de 2026

## Resultado

Se compiló y firmó un APK Android ARM64 del núcleo real de DKC1Recomp.
No es un APK de ejemplo, un lanzador vacío ni un contenedor del ejecutable Windows.
La compilación se realizó localmente con fuentes y herramientas fijadas; GitHub
Actions se utilizó para preparar dependencias y ejecutar pruebas sin una ROM.

**No se ha instalado ni ejecutado el APK en un teléfono Android.** Este resultado
valida compilación, estructura del paquete y pruebas separadas del núcleo, no la
jugabilidad móvil, el audio del teléfono, el ciclo de vida ni el rendimiento real.

## Identidad del APK de prueba

- Archivo: `DKC1Recomp-Android-v0.1.0-arm64.apk`.
- Tamaño: 10.536.686 bytes.
- SHA-256: `a75b23eaa1f95a8184d8186e2eab5a95d49083353f98c3ab4d8aec4b0b5e8e6f`.
- Paquete: `com.ylports.dkc1recomp`; versionCode 1; versionName `0.1.0-dev`.
- minSdk 23; targetSdk 35; única ABI `arm64-v8a`.
- Variante Gradle release, núcleo CMake RelWithDebInfo optimizado.
- Firmado para pruebas con certificado de desarrollo, no una clave de publicación.
- SHA-256 del certificado: `5a0003e69e60d01772ee1d23b9a0597ae82720ab70aabde15eb0ea6725d1f882`.

La clave privada no está en GitHub. El APK se entrega por separado, no se ha
publicado como release del repositorio. La ROM no está empaquetada como archivo:
cada usuario debe importarla al abrir la aplicación.

## Compilación y comprobaciones estáticas

Dependencias: NDK 28.2.13676358, CMake 3.22.1, SDK 35, Build Tools 35.0.0,
Gradle 8.11.1, AGP 8.9.2, JDK 21 en la compilación local.
Núcleo base `cb4dae77a6552790cbb7a9663957b2cb6c3e6b58`, snesrecomp
`851b11e38588818afc705e4270d3eb982b7b2af2`, SDL2
`c98c4fbff6d8f3016a3ce6685bf8f43433c3efcc`.

- Generación Python desde la ROM USA v1.0 verificada: correcta.
- `assembleDebug`: correcta.
- `assembleRelease`, incluido `lintVitalRelease`: correcta.
- Firma verificada con apksigner: esquemas v1, v2 y v3 correctos.
- ZIP verificado con `zipalign -c -P 16 4`: correcto.
- ELF64 AArch64 de `libmain.so` y `libSDL2.so`: correcto.
- Segmentos PT_LOAD de ambas bibliotecas: alineación mínima de 16.384 bytes.
- Exportación de `SDL_main` y las cinco funciones JNI de GameActivity: correcta.
- Bibliotecas nativas sin comprimir; diez archivos de avisos/licencias incluidos.
- Coincidencia de blobs de las fuentes C/Java/configuración compiladas con el
  commit `2ae8e6dab02c208db2deec29b557eb4beb696cd1`: comprobada.

Verificador reutilizable:

```sh
python android/tools/verify_apk.py --apk /ruta/app-firmada.apk --sdk /ruta/Android/Sdk --report comprobacion.json
```

## Pruebas del código del port

Pasaron localmente y en el workflow `Android source tests (no ROM)`:
264.485 comprobaciones C con ASan/UBSan, 53 comprobaciones Java y 17 pruebas Python.
Son pruebas de utilidades, controles, validación de entradas y preparación de
compilaciones; no prueban una instalación Android.

## Pruebas del núcleo real con la ROM

Se compiló un ejecutable de validación Linux x86_64 desde el mismo núcleo y las
mismas fuentes generadas. Para las declaraciones POSIX de Linux se añadieron
`-D_POSIX_C_SOURCE=200809L -D_DEFAULT_SOURCE` en esa compilación de validación;
no se modificó el submódulo ni la lógica del juego.

- Arranque desde cero, 600 fotogramas, 4:3: terminó con código 0.
- Arranque desde cero, 600 fotogramas, 16:9: terminó con código 0.
- Ruta upstream de entrada y movimiento inicial en Jungle Hijinxs, omitiendo
  únicamente la exportación de checkpoints: terminó con código 0 tras 8.070
  fotogramas. No significa haber completado el nivel entero.
- La ruta generó imagen y audio no silencioso y escribió un estado nativo.
- Carga de ese estado y repetición de 60 fotogramas: código 0; coincidieron
  exactamente los hashes de imagen, WRAM, VRAM, CGRAM, OAM y OAM fuente.

Estas pruebas no ejecutan GameActivity, JNI, SDL Android, la pantalla táctil ni
los dispositivos de audio del teléfono. No extrapolar sus tiempos a FPS móviles.
La ROM, las imágenes, el estado y las fuentes generadas permanecen privados.

## Pendiente antes de considerar una versión estable

Instalación/arranque en un teléfono ARM64; importación mediante su selector;
controles simultáneos; audio continuo; suspensión y reanudación; guardar/cargar;
pruebas de niveles y widescreen en dispositivos reales de 4 KiB y 16 KiB.
Comienza probando 4:3. El diagnóstico se exporta desde la pantalla inicial.

## Repetir la variante optimizada

Tras preparar la ROM y dependencias con `build_android.py --prepare-only`,
configura ANDROID_HOME y ejecuta `android/gradlew :app:assembleRelease` desde
el repositorio. La salida release es un APK sin firma; fírmalo con una clave
privada de desarrollo propia mediante apksigner y conserva esa clave para
actualizaciones. No publiques claves, ROMs ni código generado.
