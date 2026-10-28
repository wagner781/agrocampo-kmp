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

