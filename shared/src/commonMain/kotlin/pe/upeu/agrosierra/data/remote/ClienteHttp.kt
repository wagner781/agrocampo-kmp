package pe.upeu.agrosierra.data.remote
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.dto.ErrorDto

fun crearCliente(engine: HttpClientEngine, config: ApiConfig, seguro: AlmacenSeguro) = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys=true }) }
    install(HttpTimeout) { requestTimeoutMillis=15000; connectTimeoutMillis=10000; socketTimeoutMillis=15000 }
    install(Logging) { level=if(config.depuracion) LogLevel.INFO else LogLevel.NONE; sanitizeHeader { it==HttpHeaders.Authorization } }
    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
        seguro.leer()?.token?.let { header(HttpHeaders.Authorization,"Bearer $it") }
    }
    HttpResponseValidator { validateResponse { r ->
        if(r.status.value !in 200..299) {
            if(r.status.value==401) seguro.borrar()
            val error=try { r.body<ErrorDto>() } catch(_: Exception) { ErrorDto("HTTP ${r.status.value}") }
            throw mapearError(r.status.value,error)
        }
    } }
}
