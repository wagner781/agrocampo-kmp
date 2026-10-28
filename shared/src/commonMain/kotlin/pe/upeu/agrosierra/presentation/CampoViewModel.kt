@file:OptIn(kotlin.time.ExperimentalTime::class)
package pe.upeu.agrosierra.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*

data class Pantalla(val sesion: Sesion?=null,val lotes: List<Lote> = emptyList(),val labores: List<Labor> = emptyList(),
    val buscar: String="",val valle: String="",val lote: Lote?=null,val perfil: Boolean=false,val formulario: Labor?=null,
    val ocupado: Boolean=false,val estado: UiState=UiState.Cargando,val estadoLabores: UiState=UiState.Cargando,
    val mensaje: String="",val error: ErrorApi?=null,val campos: Map<String,String> = emptyMap(),val ultimaSync: String="Nunca")

class CampoViewModel(private val repo: CampoRepository,private val sesiones: SesionRepository,
    private val guardar: GuardarLaborUseCase,private val sincronizar: SincronizarUseCase): ViewModel() {
    private val state=MutableStateFlow(Pantalla())
    val pantalla=state.asStateFlow()
    private var listaJob: Job?=null
    private var detalleJob: Job?=null
    private var syncJob: Job?=null
    private var venceJob: Job?=null
    init {
        observarLista()
        viewModelScope.launch {
            sesiones.sesion.collect { s ->
                venceJob?.cancel()
                state.update { it.copy(sesion=s,lote=null,perfil=false,formulario=null,campos=emptyMap(),mensaje="",error=null) }
                if(s!=null) {
                    observarLista()
                    venceJob=viewModelScope.launch { delay(((s.venceEn-Clock.System.now().epochSeconds)*1000).coerceAtLeast(0)); sesiones.salir() }
                    sync()
                } else { detalleJob?.cancel() }
            }
        }
    }
    private fun observarLista() {
        listaJob?.cancel()
        val p=state.value
        listaJob=viewModelScope.launch(Dispatchers.Default) {
            repo.observarLotes(p.buscar,p.valle).catch { e -> error(e) }.collect { filas ->
                state.update { it.copy(lotes=filas,estado=if(filas.isEmpty()) UiState.Vacio else UiState.Contenido,
                    lote=it.lote?.let { l -> filas.find { f -> f.id==l.id } ?: l }) }
            }
        }
    }
    fun buscar(valor: String) { state.update { it.copy(buscar=valor) }; observarLista() }
    fun valle(valor: String) { state.update { it.copy(valle=valor) }; observarLista() }
    fun detalle(lote: Lote) {
        state.update { it.copy(lote=lote,perfil=false,formulario=null,estadoLabores=UiState.Cargando,labores=emptyList(),campos=emptyMap()) }
        detalleJob?.cancel()
        detalleJob=viewModelScope.launch(Dispatchers.Default) {
            repo.observarLabores(lote.id).catch { e -> error(e) }.collect { filas ->
                state.update { it.copy(labores=filas,estadoLabores=if(filas.isEmpty()) UiState.Vacio else UiState.Contenido) }
            }
        }
    }
    fun perfil() { state.update { it.copy(perfil=true,lote=null,formulario=null) } }
    fun volver() { state.update { it.copy(lote=if(it.formulario!=null) it.lote else null,formulario=null,perfil=false,campos=emptyMap()) } }
    fun login(usuario: String,password: String) = accion {
        if(usuario.isBlank() || password.isBlank()) throw ErrorApi.Validacion(mapOf("usuario" to "Ingrese usuario y contrasena"),"Complete sus credenciales")
        try { sesiones.login(usuario.trim(),password) } catch(e: ErrorApi.NoAutorizado) { state.update { it.copy(mensaje="Credenciales incorrectas",error=e) } }
    }
    fun salir() { sesiones.salir() }
    fun editar(l: Labor?=null) {
        val p=state.value; val lote=p.lote ?: return
        val fecha=Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        state.update { it.copy(formulario=l ?: Labor(0,lote.id,TipoLabor.RIEGO,fecha,p.sesion?.usuario ?: "",0.0,"",false),campos=emptyMap(),mensaje="",error=null) }
    }
    fun guardarFormulario(l: Labor) = accion {
        val guardada=guardar(l)
        state.update { it.copy(formulario=guardada,mensaje="Guardada localmente. Pendiente de sincronizar") }
        try {
            val resumen=sincronizar(); state.update { it.copy(formulario=null,mensaje="Enviadas ${resumen.enviados}; recibidas ${resumen.recibidos}",ultimaSync=repo.ultimaSync()) }
        } catch(e: ErrorApi.SinConexion) { state.update { it.copy(formulario=null,mensaje="Guardada sin conexion. Pendiente de sincronizar",error=null) } }
          catch(e: ErrorApi.TiempoAgotado) { state.update { it.copy(formulario=null,mensaje="Guardada localmente; servidor sin respuesta. Sincronice despues",error=e) } }
    }
    fun eliminar(l: Labor) = accion {
        repo.eliminar(l.id)
        try { sincronizar(); state.update { it.copy(mensaje="Labor eliminada",ultimaSync=repo.ultimaSync()) } }
        catch(e: ErrorApi.SinConexion) { state.update { it.copy(mensaje="Eliminacion pendiente de sincronizar") } }
    }
    fun cerrar() = accion {
        val id=state.value.lote?.id ?: return@accion
        repo.cerrar(id)
        state.update { it.copy(lote=it.lote?.copy(campaniaCerrada=true),mensaje="Campania cerrada") }
    }
    fun sync() {
        if(sesiones.sesion.value==null || syncJob?.isActive==true) return
        syncJob=accion {
            val resumen=sincronizar()
            state.update { it.copy(ultimaSync=repo.ultimaSync(),mensaje="Sincronizacion: ${resumen.enviados} enviadas, ${resumen.recibidos} recibidas") }
        }
    }
    fun actualizarPerfil()=accion { sesiones.actualizarPerfil() }
    fun limpiarMensaje() { state.update { it.copy(mensaje="",error=null,campos=emptyMap()) } }
    private fun accion(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.Default) {
        state.update { it.copy(ocupado=true,campos=emptyMap(),mensaje="",error=null) }
        try { block() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error(e) }
        finally { state.update { it.copy(ocupado=false) } }
    }
    private fun error(e: Throwable) {
        val error=e as? ErrorApi ?: ErrorApi.Protocolo()
        state.update { it.copy(mensaje=error.message,error=error,campos=(error as? ErrorApi.Validacion)?.campos ?: emptyMap()) }
    }
}
