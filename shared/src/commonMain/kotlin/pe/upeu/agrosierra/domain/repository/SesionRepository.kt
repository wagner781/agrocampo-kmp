package pe.upeu.agrosierra.domain.repository
import kotlinx.coroutines.flow.StateFlow
import pe.upeu.agrosierra.domain.model.Sesion
interface SesionRepository {
    val sesion: StateFlow<Sesion?>
    suspend fun login(usuario: String,password: String)
    suspend fun actualizarPerfil()
    fun salir()
}
