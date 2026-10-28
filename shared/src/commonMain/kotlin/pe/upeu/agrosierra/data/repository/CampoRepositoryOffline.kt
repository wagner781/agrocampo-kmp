@file:OptIn(kotlin.time.ExperimentalTime::class)
package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.random.Random
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.CampoRepository

class CampoRepositoryOffline(private val local: BaseLocal,private val api: AgroApi,private val seguro: AlmacenSeguro,
    private val mutex: Mutex) : CampoRepository {
    override fun observarLotes(buscar: String,valle: String) = local.observarLotes(buscar,valle)
    override fun observarLabores(loteId: Long) = local.observarLabores(loteId)
    override fun ultimaSync() = local.ultimaSync()
    private fun permitir(p: String) {
        if(seguro.leer()==null) throw ErrorApi.NoAutorizado()
        if(p !in seguro.leer()!!.privilegios) throw ErrorApi.SinPrivilegio("No tiene el privilegio $p")
    }
    override suspend fun guardar(labor: Labor) = mutex.withLock {
        permitir(if(labor.id==0L) "LABOR_CREAR" else "LABOR_EDITAR")
        local.db.transaction {
            val id=if(labor.id==0L) local.siguienteId() else labor.id
            val l=labor.copy(id=id,pendiente=true)
            val anterior=local.q.pendientes().executeAsList().find { it.laborId==id }
            local.guardar(l)
            local.q.encolar(id,if(id<0) "POST" else "PUT",anterior?.clave ?: "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}")
        }
    }
    override suspend fun eliminar(id: Long) = mutex.withLock {
        permitir("LABOR_ELIMINAR")
        // Sin confirmar un POST no se descarta: podría haber sido recibido por el servidor.
        if(id<0) throw ErrorApi.Conflicto("Sincronice la creacion antes de eliminarla")
        val row=local.q.laborPorId(id).executeAsOneOrNull() ?: throw ErrorApi.NoEncontrado("Labor no encontrada")
        local.db.transaction {
            local.guardar(Labor(row.id,row.loteId,TipoLabor.valueOf(row.tipo),row.fecha,row.responsable,row.cantidad,row.observacion,true))
            local.q.encolar(id,"DELETE","delete-$id")
        }
    }
    override suspend fun cerrar(loteId: Long) = mutex.withLock {
        permitir("CAMPANIA_CERRAR")
        if(local.q.pendientes().executeAsList().isNotEmpty()) throw ErrorApi.Conflicto("Sincronice las labores pendientes antes de cerrar")
        local.guardar(api.cerrar(loteId).dominio())
        Unit
    }
    override suspend fun sincronizar(): ResumenSync = mutex.withLock {
        if(seguro.leer()==null) throw ErrorApi.NoAutorizado()
        var enviados=0
        // El mismo mutex evita editar una fila mientras se envía su snapshot.
        for(p in local.q.pendientes().executeAsList()) {
            val row=local.q.laborPorId(p.laborId).executeAsOne()
            val l=Labor(row.id,row.loteId,TipoLabor.valueOf(row.tipo),row.fecha,row.responsable,row.cantidad,row.observacion,true)
            try {
                if(p.operacion=="DELETE") {
                    try { api.eliminar(l.id) } catch(_: ErrorApi.NoEncontrado) { /* DELETE repetido ya se aplicó */ }
                    local.db.transaction { local.q.eliminarLabor(l.id); local.q.desencolar(l.id) }
                } else {
                    var remoto=if(p.operacion=="POST") api.crear(l,p.clave) else api.editar(l)
                    // Un replay puede devolver la versión original si la creación perdió su respuesta.
                    if(p.operacion=="POST" && remoto.dominio().peticion()!=l.peticion()) remoto=api.editar(l.copy(id=remoto.id))
                    local.db.transaction {
                        local.q.eliminarLabor(l.id); local.q.desencolar(l.id); local.guardar(remoto.dominio().copy(pendiente=false))
                    }
                }
                enviados++
            } catch(e: ErrorApi.NoEncontrado) {
                // PUT sobre una fila eliminada en otro cliente: no se ofrece reintentar.
                local.db.transaction { local.q.eliminarLabor(l.id); local.q.desencolar(l.id) }
                descargar()
                throw e
            }
            // 400/403/409/500/red: la fila y su clave permanecen; no se borra trabajo.
        }
        val recibidos=descargar()
        local.q.guardarMeta("ultimaSync",Clock.System.now().toString())
        ResumenSync(enviados,recibidos)
    }
    private suspend fun descargar(): Int {
        val lotes=mutableListOf<Lote>(); var pagina=0
        do { val p=api.lotes(pagina++); lotes.addAll(p.contenido.map { it.dominio() }) } while(!p.ultima)
        val labores=mutableMapOf<Long,List<Labor>>()
        for(l in lotes) {
            val filas=mutableListOf<Labor>(); var n=0
            do { val p=api.labores(l.id,n++); filas.addAll(p.contenido.map { it.dominio() }) } while(!p.ultima)
            labores[l.id]=filas
        }
        // Se aplica el snapshot sólo cuando se descargaron todas las páginas.
        local.db.transaction {
            local.q.borrarLotes()
            lotes.forEach { local.guardar(it) }
            lotes.forEach { l ->
                local.q.eliminarRemotasLote(l.id)
                labores.getValue(l.id).forEach { remoto ->
                    val pendiente=local.q.laborPorId(remoto.id).executeAsOneOrNull()?.pendiente==1L
                    if(!pendiente) local.guardar(remoto.copy(pendiente=false))
                }
            }
        }
        return lotes.size+labores.values.sumOf { it.size }
    }
}

