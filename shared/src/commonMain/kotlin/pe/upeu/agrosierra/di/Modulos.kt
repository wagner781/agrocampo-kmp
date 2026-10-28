package pe.upeu.agrosierra.di
import org.koin.dsl.module
import kotlinx.coroutines.sync.Mutex
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.*
import pe.upeu.agrosierra.data.repository.*
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*

val redModule=module { single { crearCliente(get(),get(),get()) }; single { AgroApi(get()) } }
val dataModule=module {
    single { Mutex() }
    single { BaseLocal(get()) }
    single<CampoRepository> { CampoRepositoryOffline(get(),get(),get(),get()) }
    single<SesionRepository> { SesionRepositoryImpl(get(),get(),get(),get()) }
}
val domainModule=module { factory { SincronizarUseCase(get()) }; factory { GuardarLaborUseCase(get()) } }
