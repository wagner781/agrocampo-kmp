package pe.upeu.agrosierra.data.remote
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.*
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.domain.model.*

class AgroApi(private val http: HttpClient) {
    private suspend fun <T> red(block: suspend () -> T): T = try { block() }
        catch(e: CancellationException) { throw e }
        catch(e: ErrorApi) { throw e }
        catch(e: HttpRequestTimeoutException) { throw ErrorApi.TiempoAgotado() }
        catch(e: IOException) { throw ErrorApi.SinConexion() }
        catch(e: SerializationException) { throw ErrorApi.Protocolo() }
    suspend fun login(usuario: String,password: String): SesionDto = red { http.post("auth/login") { setBody(LoginDto(usuario,password)) }.body() }
    suspend fun me(): UsuarioDto = red { http.get("usuarios/me").body() }
    suspend fun lotes(pagina: Int): PaginaDto<LoteDto> = red { http.get("lotes") { parameter("pagina",pagina); parameter("tamanio",100) }.body() }
    suspend fun labores(lote: Long,pagina: Int): PaginaDto<LaborDto> = red { http.get("lotes/$lote/labores") { parameter("pagina",pagina); parameter("tamanio",100) }.body() }
    suspend fun crear(l: Labor,clave: String): LaborDto = red { http.post("labores") { header("Idempotency-Key",clave); setBody(l.peticion()) }.body() }
    suspend fun editar(l: Labor): LaborDto = red { http.put("labores/${l.id}") { setBody(l.peticion()) }.body() }
    suspend fun eliminar(id: Long) { red { http.delete("labores/$id"); Unit } } // 204: nunca body().
    suspend fun cerrar(id: Long): LoteDto = red { http.post("lotes/$id/cerrar-campania").body() }
}
