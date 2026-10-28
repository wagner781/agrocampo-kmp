package pe.upeu.agrosierra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor

/** Contra AgroAPI real en 10.0.2.2:8080; no sustituye red ni base local. */
@RunWith(AndroidJUnit4::class)
class FlujoRealTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun existe(texto: String) = compose.onAllNodesWithText(texto,substring=true).fetchSemanticsNodes().isNotEmpty()
    private fun esperar(texto: String) { compose.waitUntil(60000) { existe(texto) } }
    private fun captura(nombre: String) {
        val instrumentacion=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentacion.uiAutomation.takeScreenshot()
        val dir=File(instrumentacion.targetContext.getExternalFilesDir(null),"evidencias").apply { mkdirs() }
        File(dir,"$nombre.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        // connectedAndroidTest desinstala el paquete al terminar: conservar evidencia
        // mediante shell de instrumentación fuera de los datos privados de la app.
        shell("mkdir -p /sdcard/Download/agrocampo-evidencias")
        shell("cp ${File(dir,"$nombre.png").absolutePath} /sdcard/Download/agrocampo-evidencias/$nombre.png")
    }
    private fun shell(comando: String) {
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(comando)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
    @Test fun loginRegistroOfflineYSincronizacionReal() {
        esperar("Iniciar sesion"); captura("01-login")
        compose.onNodeWithText("Usuario").performTextInput("tecnico1")
        compose.onNodeWithText("Contrasena").performTextInput("Agro2026*")
        compose.onNodeWithText("Iniciar sesion").performClick()
        esperar("Chacra 1"); captura("02-lotes")
        compose.onNodeWithText("Chacra 1").performClick()
        esperar("Registrar labor")
        compose.waitUntil(60000) { compose.onAllNodes(hasText("Registrar labor") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        try {
            shell("cmd connectivity airplane-mode enable")
            compose.onNodeWithText("Registrar labor").performClick()
            esperar("Cantidad aplicada")
            compose.onNodeWithText("Cantidad aplicada").performTextInput("2")
            compose.onNodeWithText("Observacion").performTextInput("Evidencia offline instrumentada")
            compose.onNodeWithText("Guardar labor").performScrollTo().performClick()
            esperar("Guardada sin conexion")
            captura("03-registro-offline")
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithText("Pendiente de sincronizar",substring=true).performScrollTo()
            captura("04-pendiente")
        } finally { shell("cmd connectivity airplane-mode disable") }
        esperar("Sincronizacion:")
        compose.waitUntil(60000) { !existe("Pendiente de sincronizar") }
        captura("05-sincronizada")
    }
}
