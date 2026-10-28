package pe.upeu.agrosierra.domain.repository
import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.*

interface CampoRepository {
    fun observarLotes(buscar: String,valle: String): Flow<List<Lote>>
    fun observarLabores(loteId: Long): Flow<List<Labor>>
    suspend fun guardar(labor: Labor)
    suspend fun eliminar(id: Long)
    suspend fun cerrar(loteId: Long)
    suspend fun sincronizar(): ResumenSync
    fun ultimaSync(): String
}
