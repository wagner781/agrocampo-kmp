# Defender el código de AgroCampo

## Arquitectura y recorrido de una fila

`domain/model` conserva las clases del enunciado y no importa serialización.
`domain/repository` son contratos; `domain/usecase` coordina guardar y sincronizar.
`data/remote/dto` describe JSON y convierte al dominio. `AgroApi` llama al servicio;
`CampoRepositoryOffline` guarda los resultados en SQLDelight. `BaseLocal` expone
`asFlow().mapToList()`; `CampoViewModel` observa ese flujo y publica `StateFlow`.
`presentation/navigation/App.kt` dibuja el estado con Compose. Una pantalla no
dibuja directamente la respuesta de Ktor, ni lleva una copia manual de la base.

Una labor pasa por: tabla JPA -> `LaborRespuesta` -> JSON -> `LaborDto.dominio()`
-> `BaseLocal.guardar()` -> tabla `labor` -> `observarLaboresPorLote` -> Flow ->
`CampoViewModel` -> `Detalle`. DTO y dominio tienen responsabilidades distintas:
la representación JSON puede cambiar sin llenar el dominio de anotaciones.

`di/Modulos.kt` contiene redModule, dataModule y domainModule;
`PresentationModule.kt` declara presentationModule. `PlatformModule.android.kt`
inyecta el motor OkHttp, la base y el almacén seguro. Koin construye una única
instancia de HttpClient; no se crea un cliente en cada petición. El ViewModel
se registra como factory y la Activity lo obtiene mediante el ViewModelStore
de Android/Koin para conservarlo en cambios de configuración.

## Seguridad y sesión

`AlmacenSeguroAndroid` cifra todo el objeto de sesión con AES/GCM y un IV nuevo
en cada escritura. La clave AES vive en AndroidKeyStore y no se exporta. Las
SharedPreferences sólo contienen IV y ciphertext autenticado; el backup está
desactivado. Una sesión ilegible o vencida se descarta. `venceEn` se usa para
la navegación local; la firma y la caducidad válidas las decide el servidor.

El único punto que añade Authorization es `ClienteHttp.defaultRequest`.
Así login, consultas y escrituras comparten configuración, tiempos de espera
y traducción de errores. No se imprimen cuerpos ni credenciales en los logs.
`HttpResponseValidator` borra la sesión en 401. En 403 mantiene el token y
devuelve SinPrivilegio. El estado observable del almacén hace volver a login
inmediatamente después del borrado. El ViewModel también programa la salida
al vencer la sesión, incluso estando sin red.

Perfil muestra usuario, rol y privilegios. Los botones dependen del privilegio,
pero el backend vuelve a comprobarlo: esconder un botón no autoriza nada.
La URL está centralizada en BuildConfig y ApiConfig. `10.0.2.2` es la dirección
del host desde Android Emulator; para un teléfono real hay que configurar la
IP del PC y permitir el puerto en la red local. Release requiere un dominio
HTTPS real. HTTP sin TLS sólo está habilitado en el manifest de debug.

## Registro y sincronización sin red

Guardar valida campos en `GuardarLaborUseCase` y hace una transacción local:
fila `labor` con pendiente=1 y una entrada en `cola`. Las altas reciben un ID
`minOf(-1, minimo-1)`, mientras JPA asigna IDs positivos. La cola persiste aunque
el sistema mate el proceso. Cada POST conserva su clave de idempotencia.

`SincronizarUseCase` delega al repositorio. Primero envía TODA la cola y después
descarga TODAS las páginas de lotes y sus labores. Un mutex compartido impide
modificar una fila mientras se transmite o cambiar de cuenta a mitad del envío.
Después de un POST confirmado, una transacción elimina el ID provisional,
guarda el definitivo y quita la cola. El observable actualiza el distintivo.
Un PUT usa el ID existente. DELETE comprueba éxito y nunca deserializa un 204.

Si el servidor confirma un POST pero se pierde la respuesta, el reintento lleva
la misma clave. El backend devuelve el ID previo. Si el usuario corrigió el
borrador entre ambos intentos, se aplica PUT antes de reemplazarlo. DELETE
repetido puede devolver 404 y se trata como eliminación ya confirmada.
No se permite eliminar un ID provisional de resultado incierto: primero se
sincroniza para resolver si existe en el servidor.

Una falla 400/403/409/500 o de red conserva la cola; no se baja un snapshot sobre
las escrituras pendientes. Se descargan todas las páginas antes de aplicar el
snapshot en una transacción. Los conflictos no se solucionan inventando datos:
se muestra el motivo y se conserva la fila para corrección. Un PUT que obtiene
404 descarta esa referencia local, refresca y no ofrece reintento.

Logout elimina el token, pero conserva pendientes de su propietario. Otra
cuenta no puede iniciar sesión sobre pendientes ajenos. Cuando ya no hay cola,
cambiar de cuenta limpia la caché antes de mostrar datos de la nueva cuenta.
El registro inicial requiere al menos una sincronización online: la app no
incluye lotes ni labores incrustadas. La lista de tres valles del selector es
configuración del caso, no datos operativos simulados.

`MainActivity` registra un callback de ConnectivityManager mientras es visible.
Al recuperar una red validada, ejecuta la misma sincronización manual. Al volver
a abrir una sesión vigente también se sincroniza. No hay servicio permanente
de fondo ni promesa de sincronizar con la aplicación cerrada o el proceso muerto.

## Estados y errores

`UiState` distingue carga, contenido, vacío y error. `Pantalla` mantiene progreso,
selección, mensajes y errores por campo. 400 coloca validationErrors bajo los
TextField; 401 vuelve a login; 403 conserva sesión; 404 refresca sin reintentar;
409 muestra la regla; 500 y tiempos agotados ofrecen reintento; sin red permite
seguir con la caché y encolar cambios. Las validaciones de negocio siguen en
el servicio aunque el formulario haga validación preliminar.

El borrador conserva el ID provisional tras un error del servidor: corregirlo
actualiza esa fila y evita crear otra. Los mensajes de rechazo de campos de la
cola se muestran en el formulario activo; revisar primero los pendientes si
se acumularon varias operaciones antes de una sincronización fallida.

## Pruebas y demostración

`shared/src/commonTest/.../OfflineTest.kt` usa MockEngine para HTTP y una función
expect/actual para crear SQLite JDBC en memoria en jvmTest. Es una base SQLDelight
real, no una lista fake. Se verifican conversión, 400 por campo, 401 frente a 403,
registro sin red y orden de envío/descarga con reemplazo sin duplicados.

`FlujoRealTest` es una prueba Android que necesita el backend arrancado en el PC.
Inicia sesión, sincroniza, activa modo avión, registra, conserva el pendiente,
recupera red y espera la sincronización. Guarda capturas reales en el directorio
externo de la app. Puede ejecutarse en un emulador limpio con:
`gradlew.bat :androidApp:connectedDebugAndroidTest`.

Comandos reproducibles:

```
gradlew.bat :shared:jvmTest :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Para la defensa: mostrar consulta SQL y Flow, registrar en modo avión, explicar
la fila negativa y la cola, recuperar conexión y enseñar el mismo registro con
ID positivo en el servidor. Comparar 401 y 403 usando la colección de AgroAPI.
Agregar un rol exige cambiar la matriz del backend y su semilla; la aplicación
usa la lista de privilegios recibida sin condicionales por nombre de rol.

El historial empieza el 28 de octubre de 2026. No demuestra sesiones anteriores,
ni un conflicto Git que no ocurrió. La rama SC requiere la letra y apellido que
asigne el docente. La etiqueta de evaluación debe acordarse sobre una versión
revisada; una ejecución automatizada no equivale a la defensa individual.

Referencias: [SQLDelight Flow](https://sqldelight.github.io/sqldelight/2.1.0/multiplatform_sqlite/coroutines/),
[Ktor validación](https://ktor.io/docs/client-response-validation.html),
[compatibilidad Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html).
