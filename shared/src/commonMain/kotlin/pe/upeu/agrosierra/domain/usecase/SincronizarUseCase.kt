package pe.upeu.agrosierra.domain.usecase
import pe.upeu.agrosierra.domain.repository.CampoRepository
class SincronizarUseCase(private val repo: CampoRepository) {
    suspend operator fun invoke() = repo.sincronizar()
}
