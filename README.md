# AgroCampo

Aplicación Kotlin Multiplatform orientada a Android. `shared` contiene el
código común y un target JVM para ejecutar pruebas sin emulador; `androidApp`
contiene la Activity, el driver SQLDelight y el almacén seguro de Android.

## Compilar

Requisitos: JDK 21, Android SDK 36 y build tools instaladas. Configurar la ruta
del SDK en `local.properties` (`sdk.dir=...`) o `ANDROID_HOME`.

Windows: `./gradlew.bat :androidApp:assembleDebug :shared:jvmTest`.
Linux/macOS: `./gradlew :androidApp:assembleDebug :shared:jvmTest`.
Abrir la carpeta en Android Studio y ejecutar androidApp en un emulador.
El emulador accede al backend del PC por `http://10.0.2.2:8080/api/v1/`.
El permiso HTTP sin cifrar existe solo en debug para la demostración local.
Antes de distribuir release se debe configurar el dominio HTTPS real en Gradle.

## Desarrollo

`main` conserva entregas estables; `develop` integra PR desde `feature/...` con
merge commit. El commit raíz inicia la infraestructura; no contiene funciones
de negocio. Se usan fechas reales. El repositorio nace el 28 de octubre de 2026
y no acredita desarrollo durante sesiones anteriores.

## Plan de implementación

1. Dominio, DTO, Ktor único y SQLDelight observable.
2. Sesión segura, repositorios offline, cola y sincronización.
3. Presentación Compose, ViewModel/StateFlow, Koin y pruebas commonTest.

La solicitud de cambio queda pendiente de asignación del docente. No se
simulan evidencias del emulador ni resultados de evaluación.

## Arquitectura, contrato y decisiones

`domain` contiene modelos, interfaces y casos de uso; `data` contiene DTO/Ktor,
SQLDelight y repositorios offline; `presentation` contiene ViewModel/StateFlow y
Compose; `di` declara módulos Koin. Android implementa los contratos de plataforma.
[Guía de defensa y recorrido del código](docs/DEFENSA.md).

API: rutas `/auth/login`, `/usuarios/me`, `/lotes`, `/lotes/{id}`,
`/lotes/{id}/labores`, `/labores`, `/labores/{id}` y
`/lotes/{id}/cerrar-campania`. Los listados consumen el envoltorio `contenido`,
`pagina`, `tamanio`, `totalElementos`, `totalPaginas`, `ultima` y todas las páginas.
El dominio conserva los tipos exigidos en el PDF.

Usuarios del backend: `tecnico1`, `jefe1`, `admin`; contraseña de demostración
`Agro2026*`. Técnico lee/crea/edita propias en 24 h; jefe además elimina y cierra;
administrador lee y gestiona usuarios según su privilegio. La seguridad definitiva
siempre vive en el backend. La app no contiene datos de lotes ni labores semilla.

El token se cifra con AndroidKeyStore y AES-GCM. La interfaz observa SQLDelight.
Los cambios se guardan con pendiente y cola durable; sincronizar sube antes de
bajar y usa idempotencia para resolver reintentos. Consultar la guía para el
tratamiento de errores, cuentas y conflictos.

Pruebas comunes ejecutables: `./gradlew.bat :shared:jvmTest`.
La prueba Android real necesita emulador limpio y AgroAPI en el puerto 8080:
`./gradlew.bat :androidApp:connectedDebugAndroidTest`.

## Evidencias verificadas

[PDF con capturas reales, respuestas HTTP y grafo Git](docs/evidencias/AgroSierra-evidencias.pdf).
La carpeta `docs/evidencias` incluye los PNG originales, resúmenes JUnit XML y
JSON de las comprobaciones contra el servidor. `sincronizacion-real.json`
confirma una única fila positiva para la última operación instrumentada.
Los resultados son de una ejecución real y no sustituyen la defensa.
El script `docs/generar_evidencias.py` vuelve a componer el PDF con reportlab y
Pillow a partir de los informes/capturas existentes; no inventa resultados.

