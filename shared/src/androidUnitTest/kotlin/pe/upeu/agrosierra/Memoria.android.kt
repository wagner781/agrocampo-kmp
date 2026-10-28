package pe.upeu.agrosierra
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.upeu.agrosierra.db.AgroDb
// Las pruebas unitarias Android corren en la JVM del host y no en el dispositivo.
actual fun memoria(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AgroDb.Schema.create(it) }
