package pe.upeu.agrosierra.presentation
import pe.upeu.agrosierra.domain.model.ErrorApi
sealed interface UiState {
    data object Cargando: UiState
    data object Contenido: UiState
    data object Vacio: UiState
    data class Error(val causa: ErrorApi): UiState
}
