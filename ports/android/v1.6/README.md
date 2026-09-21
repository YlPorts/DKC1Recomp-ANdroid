# DKC1 Android 1.6 — fuente de la línea estable basada en 1.5

Esta carpeta conserva el delta de Android probado sobre la línea **DKC1 Android 1.5**
(`com.ylports.dkc1recomp.mobile`). Se añadió de forma aislada para no alterar el
host de escritorio ni la recompilación principal del repositorio.

## Estado de esta revisión

- `versionName`: **1.6**
- `versionCode`: **109**
  - 107/108 se usaron en artefactos de prueba fallidos durante el diagnóstico.
  - 109 evita un downgrade al instalar la revisión estable sobre esos dispositivos.
- Base APK 1.5 SHA-256:
  `7ae498c0b0818a874090caecf0462a5afbf962f29df27f274acc495c3aa725cb`
- APK 1.6 MSU-1 Folder Fix SHA-256:
  `de95188b2c63570bad96f700fada2aef37a8dedfa576c0e389a0a50cca737f25`
- Certificado de firma esperado SHA-256:
  `d054ee3bb0a45e7c7625767c35aeeb8c2d66ec66e9b9570a1da3d627c13ae4b6`

La clave privada **no** se guarda en GitHub.

## Corrección MSU-1

La 1.5 solo reconocía nombres `track-N.pcm`, `dkc_msu-N.pcm` y `dkc-N.pcm`.
Al seleccionar una carpeta con nombres normales, por ejemplo
`01 - Jungle Hijinxs.pcm`, el PCM se clasificaba como archivo auxiliar y se le
aplicaba el límite auxiliar de 8 MiB. Por eso aparecía falsamente
`El pack excede el límite de tamaño` incluso con una sola pista.

`MusicPack.java` ahora:

- detecta el último grupo numérico válido antes de `.pcm`;
- acepta nombres como `01 - Jungle Hijinxs.pcm`,
  `Donkey Kong Country HD - 02.pcm`, `DKC1_27.pcm` y los nombres anteriores;
- mantiene copia por streaming sin cargar una pista completa en RAM;
- usa un máximo de seguridad de 2 GiB por pista y 16 GiB por pack;
- separa el presupuesto de archivos auxiliares del presupuesto PCM;
- comprueba espacio libre durante copias grandes;
- conserva la instalación transaccional de la 1.5;
- sigue exigiendo las pistas 1–27 antes de activar la sustitución MSU-1.

La misma lógica se comparte entre ZIP y `MusicFiles.importFolder()`, de modo que
seleccionar una carpeta y seleccionar un ZIP pasan por la misma validación.

## Prueba

```sh
javac -d out \
  MusicPack.java \
  MsuImportRegressionTest.java
java -cp out com.ylports.dkc1recomp.MsuImportRegressionTest
```

Resultado esperado:

```text
MSU-1 v1.6 regression tests passed: 6
```

## Rambi / bonus de Jungle Hijinxs

El reporte de tirones, parpadeos y texturas corruptas cerca del bonus de Rambi
se conserva como trabajo pendiente. La propuesta de fallback seguro está en
`experimental-android-bonus-safe.patch`, pero **no se aplica por defecto**.

Durante las pruebas se comprobó que mezclar cambios binarios del renderer con
la corrección MSU-1 podía producir APKs que no arrancaban. Por seguridad, esta
actualización del repositorio no toca `runner/`, WRAM, VRAM, OAM ni `libmain.so`.
El parche experimental solo queda archivado para continuar el diagnóstico desde
fuente y con una prueba real en dispositivo.

El proyecto principal de escritorio queda sin cambios.
