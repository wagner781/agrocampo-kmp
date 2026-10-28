package pe.upeu.agrosierra
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.agrosierra.db.AgroDb
fun crearBaseAndroid(context: Context) = AgroDb(AndroidSqliteDriver(AgroDb.Schema,context,"agrocampo.db"))
