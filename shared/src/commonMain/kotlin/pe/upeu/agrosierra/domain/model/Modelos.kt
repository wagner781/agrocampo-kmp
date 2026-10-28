package pe.upeu.agrosierra.domain.model
enum class TipoLabor { RIEGO, FUMIGACION, COSECHA }
data class Labor(val id: Long,val loteId: Long,val tipo: TipoLabor,val fecha: String,val responsable: String,
    val cantidad: Double,val observacion: String,val pendiente: Boolean)
data class Sesion(val token: String,val usuario: String,val rol: String,val privilegios: Set<String>,val venceEn: Long = 0)
data class ResumenSync(val enviados: Int,val recibidos: Int)
