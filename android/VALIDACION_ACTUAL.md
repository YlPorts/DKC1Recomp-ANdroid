# Validación Android v0.3.0-dev

Fecha: 2026-09-12. Base del núcleo: cb4dae77a6552790cbb7a9663957b2cb6c3e6b58.
Pin de snesrecomp: 851b11e38588818afc705e4270d3eb982b7b2af2.
La ROM USA v1.0 suministrada coincide con el SHA-256 exigido por el proyecto.

## Ejecutado en esta sesión

- Generador original `scripts/generate_snesrecomp.py --analysis-backend python`:
  terminado, con los overrides de presentación originales. No se publican sus
  fuentes generadas ni la ROM.
- `:app:assembleRelease` y `:app:lintRelease`: completados con SDK 35,
  Build Tools 35.0.0, NDK 28.2.13676358, CMake 3.22.1, Gradle 8.11.1 y JDK 21
  (fuentes Java nivel 17).
- Lint: 0 errores y 37 advertencias, con excepciones acotadas y justificadas
  para SDL2 en `lint.xml`; no se declara que el código esté libre de advertencias.
- Utilidades C Android con ASan/UBSan: 264485 aserciones aprobadas.
- Pruebas Java de controles y rechazo de ROM: 53 aserciones aprobadas.
- Pruebas Java de copia de seguridad y geometría: 2018 aserciones aprobadas,
  incluida igualdad de tamaños y ausencia de solapamientos por defecto en seis
  resoluciones y tres escalas. Una disposición manual sí puede solaparse.
- 17 pruebas Python de herramientas/contratos: aprobadas localmente.
- Prueba original `test_desktop_graphics.c` con los módulos de PC y ASan/UBSan:
  aprobada; verifica que los modelos de color no mutan la imagen fuente.
- Firma APK v1/v2/v3, bibliotecas ARM64 sin comprimir, segmentos ELF y ZIP
  alineados a 16 KiB, seis enlaces JNI y SDL_main, y avisos de licencia: verificados.

APK: `DKC1Recomp-Android-v0.3.0-mobile-arm64.apk`.
Tamaño: 10565414 bytes.
SHA-256: `b360ea526d8ada45da4d2b6e3a289e7e74dd087a9bf04eda556e4c941f106fa8`.
Paquete estable nuevo: `com.ylports.dkc1recomp.mobile`, versionCode 3.
Certificado SHA-256: `d054ee3bb0a45e7c7625767c35aeeb8c2d66ec66e9b9570a1da3d627c13ae4b6`.
No puede actualizar la v0.2 firmada con otra clave. Se entrega separadamente
un respaldo privado de la nueva clave al propietario; no se publica en GitHub.

## No comprobado todavía

- Instalación/ejecución de la **v0.3** en un dispositivo Android.
- Interacción visual de los nuevos menús, arrastre, gestos y suspensión en el
  teléfono del usuario. La geometría se probó numéricamente, no con capturas simuladas.
- Rendimiento móvil, sonido real, Bluetooth/USB de dispositivos específicos.
- Restauración de partidas reales entre instalaciones y continuidad automática
  durante una sesión larga. Las pruebas de ZIP usan datos de prueba identificados.
- Baby Kong con DKC3 real, todos los niveles y modos ultrawide/acuáticos.

La confirmación de gameplay del usuario corresponde a versiones anteriores,
no se reutiliza como prueba de la v0.3. No se alteró `main` ni el submódulo.
