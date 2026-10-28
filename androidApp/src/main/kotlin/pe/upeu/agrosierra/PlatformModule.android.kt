package pe.upeu.agrosierra
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.ApiConfig

val platformModule=module {
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single { crearBaseAndroid(androidContext()) }
    single<HttpClientEngine> { OkHttp.create() }
    single { ApiConfig(BuildConfig.API_URL,BuildConfig.DEBUG) }
}
