package pe.upeu.agrosierra.data.remote
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.data.remote.dto.ErrorDto
fun mapearError(status: Int, error: ErrorDto): ErrorApi = when(status) {
    400 -> ErrorApi.Validacion(error.validationErrors,error.message)
    401 -> ErrorApi.NoAutorizado()
    403 -> ErrorApi.SinPrivilegio(error.message)
    404 -> ErrorApi.NoEncontrado(error.message)
    409 -> ErrorApi.Conflicto(error.message)
    in 500..599 -> ErrorApi.Servidor()
    else -> ErrorApi.Protocolo()
}
