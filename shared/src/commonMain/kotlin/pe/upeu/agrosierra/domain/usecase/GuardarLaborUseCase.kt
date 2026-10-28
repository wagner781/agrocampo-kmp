package pe.upeu.agrosierra.domain.usecase
import kotlinx.datetime.LocalDate
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.CampoRepository
class GuardarLaborUseCase(private val repo: CampoRepository) {
    suspend operator fun invoke(l: Labor) {
        val errores=mutableMapOf<String,String>()
        if(l.responsable.isBlank()) errores["responsable"]="Ingrese el responsable"
        if(l.responsable.length>120) errores["responsable"]="Maximo 120 caracteres"
        if(!l.cantidad.isFinite() || l.cantidad<=0) errores["cantidad"]="Ingrese una cantidad positiva"
        if(l.observacion.length>1000) errores["observacion"]="Maximo 1000 caracteres"
        try { LocalDate.parse(l.fecha) } catch(_: IllegalArgumentException) { errores["fecha"]="Use una fecha valida AAAA-MM-DD" }
        if(errores.isNotEmpty()) throw ErrorApi.Validacion(errores,"Revise los campos")
        repo.guardar(l)
    }
}
