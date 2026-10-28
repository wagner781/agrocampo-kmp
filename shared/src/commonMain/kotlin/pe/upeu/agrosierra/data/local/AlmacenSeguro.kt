package pe.upeu.agrosierra.data.local
import pe.upeu.agrosierra.domain.model.Sesion
interface AlmacenSeguro {
    val sesion: kotlinx.coroutines.flow.StateFlow<Sesion?>
    fun leer(): Sesion?
    fun guardar(sesion: Sesion)
    fun borrar()
}
