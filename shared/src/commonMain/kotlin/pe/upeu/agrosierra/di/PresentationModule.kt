package pe.upeu.agrosierra.di
import org.koin.dsl.module
import pe.upeu.agrosierra.presentation.CampoViewModel
val presentationModule=module { factory { CampoViewModel(get(),get(),get(),get()) } }
