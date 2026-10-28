package pe.upeu.agrosierra.domain.model
sealed class ErrorApi(override val message: String) : Exception(message) {
    class Validacion(val campos: Map<String,String>, mensaje: String) : ErrorApi(mensaje)
    class NoAutorizado : ErrorApi("Inicie sesion nuevamente")
    class SinPrivilegio(m: String) : ErrorApi(m)
    class NoEncontrado(m: String) : ErrorApi(m)
    class Conflicto(m: String) : ErrorApi(m)
    class Servidor : ErrorApi("El servidor no esta disponible. Intente nuevamente")
    class SinConexion : ErrorApi("Sin conexion. Los datos guardados siguen disponibles")
    class TiempoAgotado : ErrorApi("Se agoto el tiempo de espera")
    class Protocolo : ErrorApi("Respuesta del servidor no reconocida")
}
