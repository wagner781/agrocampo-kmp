package pe.upeu.agrosierra.data.repository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.SesionRepository

class SesionRepositoryImpl(private val api: AgroApi,private val seguro: AlmacenSeguro,private val local: BaseLocal,
    private val mutex: Mutex): SesionRepository {
    override val sesion = seguro.sesion
    override suspend fun login(usuario: String,password: String) = mutex.withLock {
        val propietario=local.q.propietario().executeAsOneOrNull()
        if(propietario!=null && propietario!=usuario && local.q.pendientes().executeAsList().isNotEmpty())
            throw ErrorApi.Conflicto("Hay labores pendientes de $propietario. Ingrese con esa cuenta y sincronice primero")
        val dto=api.login(usuario,password)
        if(propietario!=usuario) local.limpiar()
        local.q.guardarMeta("propietario",usuario)
        seguro.guardar(Sesion(dto.token,dto.usuario,dto.rol,dto.privilegios,Clock.System.now().epochSeconds+dto.expiraEn))
    }
    override suspend fun actualizarPerfil() {
        val anterior=seguro.leer() ?: throw ErrorApi.NoAutorizado()
        val u=api.me()
        seguro.guardar(anterior.copy(usuario=u.usuario,rol=u.rol,privilegios=u.privilegios))
    }
    override fun salir() { seguro.borrar() }
}
