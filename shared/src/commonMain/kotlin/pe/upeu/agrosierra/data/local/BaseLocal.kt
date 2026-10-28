package pe.upeu.agrosierra.data.local
import app.cash.sqldelight.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.*

class BaseLocal(val db: AgroDb) {
    val q = db.agroDbQueries
    fun observarLotes(buscar: String,valle: String) = q.observarLotes("%$buscar%","%$buscar%",valle,valle)
        .asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { Lote(it.id,it.codigo,it.nombre,it.hectareas,it.cultivo,it.valle,it.campaniaCerrada!=0L,it.dosisMaxima) } }
    fun observarLabores(id: Long) = q.observarLaboresPorLote(id).asFlow().mapToList(Dispatchers.Default)
        .map { rows -> rows.map { Labor(it.id,it.loteId,TipoLabor.valueOf(it.tipo),it.fecha,it.responsable,it.cantidad,it.observacion,it.pendiente!=0L) } }
    fun guardar(l: Lote) = q.guardarLote(l.id,l.codigo,l.nombre,l.hectareas,l.cultivo,l.valle,if(l.campaniaCerrada) 1L else 0L,l.dosisMaximaPorHectarea)
    fun guardar(l: Labor) = q.guardarLabor(l.id,l.loteId,l.tipo.name,l.fecha,l.responsable,l.cantidad,l.observacion,if(l.pendiente) 1L else 0L)
    fun siguienteId() = minOf(-1L,q.siguienteIdLocal().executeAsOne()-1)
    fun limpiar() = db.transaction { q.borrarCola(); q.borrarLabores(); q.borrarLotes(); q.borrarMeta() }
    fun ultimaSync(): String = q.ultimaSync().executeAsOneOrNull() ?: "Nunca"
}
