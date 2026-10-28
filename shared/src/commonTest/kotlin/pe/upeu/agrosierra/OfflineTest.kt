package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.*
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.data.repository.*
import pe.upeu.agrosierra.domain.model.*

expect fun memoria(): SqlDriver
class SeguroMemoria: AlmacenSeguro {
    private val state=MutableStateFlow<Sesion?>(Sesion("token","tecnico1","TECNICO",setOf("LABOR_CREAR","LABOR_EDITAR"),Long.MAX_VALUE))
    override val sesion=state.asStateFlow()
    override fun leer()=state.value
    override fun guardar(sesion: Sesion) { state.value=sesion }
    override fun borrar() { state.value=null }
}
class OfflineTest {
    private fun nueva()=Labor(0,1,TipoLabor.RIEGO,"2026-10-28","Tecnico",2.0,"Prueba",false)
    private val headers=headersOf(HttpHeaders.ContentType,"application/json")
    private val loteJson="""{"id":1,"codigo":"L1","nombre":"Lote","hectareas":2.0,"cultivo":"Papa","valle":"Colca","campaniaCerrada":false,"dosisMaximaPorHectarea":3.0}"""
    private val laborJson="""{"id":99,"loteId":1,"tipo":"RIEGO","fecha":"2026-10-28","responsable":"Tecnico","cantidad":2.0,"observacion":"Prueba"}"""
    private fun pagina(json: String)="""{"contenido":[$json],"pagina":0,"tamanio":100,"totalElementos":1,"totalPaginas":1,"ultima":true}"""
    @Test fun conversionDtoConservaModelo() {
        val dto=LoteDto(1,"L1","Lote",2.0,"Papa","Colca",false,3.0)
        assertEquals(Lote(1,"L1","Lote",2.0,"Papa","Colca",false,3.0),dto.dominio())
        assertEquals(TipoLabor.RIEGO,LaborDto(1,1,"RIEGO","2026-10-28","Tecnico",2.0,"",false).dominio().tipo)
    }
    @Test fun error400TransportaCampos()=runTest {
        val seguro=SeguroMemoria()
        val client=crearCliente(MockEngine { respond("""{"message":"Dosis excedida","validationErrors":{"cantidad":"Maximo 6"}}""",HttpStatusCode.BadRequest,headers) },ApiConfig("http://localhost/api/v1/"),seguro)
        try {
            val error=assertFailsWith<ErrorApi.Validacion> { AgroApi(client).crear(nueva(),"k") }
            assertEquals("Maximo 6",error.campos["cantidad"])
        } finally { client.close() }
    }
    @Test fun diferencia401Y403()=runTest {
        for(status in listOf(HttpStatusCode.Unauthorized,HttpStatusCode.Forbidden)) {
            val seguro=SeguroMemoria()
            val client=crearCliente(MockEngine { respond("""{"message":"Denegado"}""",status,headers) },ApiConfig("http://localhost/api/v1/"),seguro)
            try {
                val e=assertFailsWith<ErrorApi> { AgroApi(client).me() }
                if(status==HttpStatusCode.Unauthorized) { assertIs<ErrorApi.NoAutorizado>(e); assertNull(seguro.leer()) }
                else { assertIs<ErrorApi.SinPrivilegio>(e); assertNotNull(seguro.leer()) }
            } finally { client.close() }
        }
    }
    @Test fun registroSinConexionPersisteNegativoYObservable()=runTest {
        val driver=memoria(); val seguro=SeguroMemoria(); var llamadas=0
        val client=crearCliente(MockEngine { llamadas++; throw IOException("sin red") },ApiConfig("http://localhost/api/v1/"),seguro)
        try {
            val local=BaseLocal(AgroDb(driver)); val repo=CampoRepositoryOffline(local,AgroApi(client),seguro,Mutex())
            repo.guardar(nueva())
            val filas=repo.observarLabores(1).first()
            assertEquals(0,llamadas); assertEquals(-1,filas.single().id); assertTrue(filas.single().pendiente)
            assertFailsWith<ErrorApi.SinConexion> { repo.sincronizar() }
            assertEquals(1,local.q.pendientes().executeAsList().size)
            assertTrue(repo.observarLabores(1).first().single().pendiente)
        } finally { client.close(); driver.close() }
    }
    @Test fun sincronizacionSubeAntesDeBajarYReemplazaSinDuplicar()=runTest {
        val driver=memoria(); val seguro=SeguroMemoria(); val orden=mutableListOf<String>()
        val client=crearCliente(MockEngine { req ->
            orden+="${req.method.value} ${req.url.encodedPath}"
            when {
                req.method==HttpMethod.Post -> { assertNotNull(req.headers["Idempotency-Key"]); respond(laborJson,HttpStatusCode.Created,headers) }
                req.url.encodedPath.endsWith("/lotes") -> respond(pagina(loteJson),HttpStatusCode.OK,headers)
                else -> respond(pagina(laborJson),HttpStatusCode.OK,headers)
            }
        },ApiConfig("http://localhost/api/v1/"),seguro)
        try {
            val local=BaseLocal(AgroDb(driver)); val repo=CampoRepositoryOffline(local,AgroApi(client),seguro,Mutex())
            repo.guardar(nueva()); val resumen=repo.sincronizar()
            assertEquals(listOf("POST /api/v1/labores","GET /api/v1/lotes","GET /api/v1/lotes/1/labores"),orden)
            assertEquals(1,resumen.enviados); assertEquals(2,resumen.recibidos)
            assertEquals(99L,repo.observarLabores(1).first().single().id)
            assertFalse(repo.observarLabores(1).first().single().pendiente)
            assertTrue(local.q.pendientes().executeAsList().isEmpty())
            repo.sincronizar(); assertEquals(1,repo.observarLabores(1).first().size)
        } finally { client.close(); driver.close() }
    }
}
