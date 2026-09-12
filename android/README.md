# DKC1Recomp Android — development port

Spanish / Español

Frontend Android ARM64 que utiliza el núcleo real de este fork. No ejecuta el EXE
de Windows ni sustituye el juego por una demostración. La rama `main` conserva
el proyecto de escritorio; la adaptación se desarrolla en `android/initial-port`.

## Compilar

Usa Python 3.10+, Git, JDK 17 o 21, SDK Android 35, Build Tools 35.0.0,
CMake 3.22.1 y NDK 28.2.13676358. Gradle 8.11.1 y AGP 8.9.2 están fijados.
Instala los componentes con Android Studio o sdkmanager y revisa sus licencias.

```sh
git clone --branch android/initial-port https://github.com/YlPorts/DKC1Recomp-ANdroid.git
cd DKC1Recomp-ANdroid
python android/tools/build_android.py --rom "/ruta/privada/dkc1.sfc" --sdk "/ruta/Android/Sdk"
```

La herramienta verifica la ROM, descarga las revisiones fijadas de snesrecomp y
SDL2, genera código C fuera del control de versiones y ejecuta Gradle.
Salida de depuración: `android/app/build/outputs/apk/debug/app-debug.apk`.
No publica el APK ni la ROM. No acepta automáticamente licencias.

Para regenerar fuentes tras cambios intencionados, `--regenerate` conserva antes
un respaldo. `--prepare-only` prepara dependencias y fuentes sin invocar el NDK.
`--verify-only` solo comprueba la ROM. No se acepta ningún hash alternativo.

## ROM compatible

Donkey Kong Country USA v1.0, exactamente 4.194.304 bytes sin cabecera:

```
fa8cacf5bbfc39ee6bbaa557adf89133d60d42f6cf9e1db30d5a36a469f74d15
```

Se admite la misma ROM con cabecera de copiador de 512 bytes. `.sfc` y `.smc`
son válidos; ZIP, PAL y otras revisiones no. La ROM no está en el repositorio
ni se empaqueta como archivo dentro del APK. El código nativo se genera a partir
de la ROM, como en el proyecto original.

## Uso

Objetivo: Android 6.0+ con sistema ARM64 y OpenGL ES 2.0. Instala el APK, abre
la app y elige tu ROM compatible. Se importa al almacenamiento privado.
Empieza por 4:3; 16:10 y 16:9 reutilizan las rutas experimentales upstream.

Controles: cruceta, B para saltar, Y para correr/rodar/agarrar, X/A/L/R,
SELECT y START. Se pueden mantener varios botones con varios dedos. `II` o
Atrás abre pausa, estado rápido, audio y salida. Los mandos SDL usan posiciones
físicas equivalentes a SNES. No hay pantalla de remapeo en esta primera versión.

Las partidas normales son SRAM privada. Los estados rápidos se separan por
formato de pantalla. Un guardado inválido se conserva antes de reemplazarlo;
un fallo al cargar un estado detiene la sesión sin persistir memoria alterada.
La pantalla inicial permite exportar el diagnóstico nativo. Desinstalar borra
los datos privados: no desinstales una versión con partidas sin respaldarlas.

## Validación y límites

```sh
python android/tools/run_tests.py
```

El workflow `Android source tests (no ROM)` valida utilidades C con sanitizadores,
modelo de controles y verificador Java, y herramientas Python sin una ROM.
Eso no equivale a probar el juego en Android. La compilación/enlace NDK, firma
APK y las pruebas reales de arranque/audio/jugabilidad deben reportarse por
separado. Consulta `VALIDACION_ACTUAL.md` cuando esté presente.

El port no incluye los menús de escritorio, CRT avanzado, música MSU-1, Baby Kong
ni editor de controles. Tampoco promete ultrawide arbitrario ni 60 FPS en todos
los teléfonos. Los APK de desarrollo no tienen firma de publicación; conserva
la clave de prueba para que futuras actualizaciones sean compatibles.

No subas ROMs, fuentes generadas, assets extraídos, saves o claves a GitHub.
Conserva las licencias raíz, las del submódulo y `android/THIRD_PARTY_NOTICES.md`.
