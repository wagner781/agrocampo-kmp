package pe.upeu.agrosierra.di
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import pe.upeu.agrosierra.presentation.CampoViewModel
val presentationModule=module { viewModel { CampoViewModel(get(),get(),get(),get()) } }
