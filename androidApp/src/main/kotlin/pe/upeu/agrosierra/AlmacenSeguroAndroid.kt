package pe.upeu.agrosierra

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.domain.model.Sesion

class AlmacenSeguroAndroid(context: Context): AlmacenSeguro {
    private val prefs=context.getSharedPreferences("sesion_cifrada",Context.MODE_PRIVATE)
    private val alias="agrocampo.jwt.aes"
    private fun clave(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun restaurar(): Sesion? = try {
        val raw=prefs.getString("datos",null)
        if(raw==null) null else {
            val partes=raw.split(':')
            val c=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,clave(),GCMParameterSpec(128,Base64.decode(partes[0],Base64.NO_WRAP))) }
            val json=JSONObject(String(c.doFinal(Base64.decode(partes[1],Base64.NO_WRAP)),Charsets.UTF_8))
            val permisos=json.getJSONArray("privilegios")
            Sesion(json.getString("token"),json.getString("usuario"),json.getString("rol"),
                (0 until permisos.length()).map { permisos.getString(it) }.toSet(),json.getLong("venceEn"))
                .takeIf { it.venceEn>System.currentTimeMillis()/1000 }
        }
    } catch(_: Exception) { null }
    private val estado=MutableStateFlow(restaurar())
    override val sesion=estado.asStateFlow()
    init { if(estado.value==null) prefs.edit().clear().commit() }
    @Synchronized override fun leer(): Sesion? {
        if(estado.value?.let { it.venceEn<=System.currentTimeMillis()/1000 }==true) borrar()
        return estado.value
    }
    @Synchronized override fun guardar(sesion: Sesion) {
        val json=JSONObject().put("token",sesion.token).put("usuario",sesion.usuario).put("rol",sesion.rol)
            .put("privilegios",JSONArray(sesion.privilegios.toList())).put("venceEn",sesion.venceEn)
        val c=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,clave()) }
        val cifrado=c.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("datos",Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cifrado,Base64.NO_WRAP)).commit())
        estado.value=sesion
    }
    @Synchronized override fun borrar() { check(prefs.edit().clear().commit()); estado.value=null }
}
