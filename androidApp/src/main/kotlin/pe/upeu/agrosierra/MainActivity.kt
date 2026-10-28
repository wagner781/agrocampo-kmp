package pe.upeu.agrosierra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pe.upeu.agrosierra.presentation.navigation.AgroCampo
import pe.upeu.agrosierra.presentation.CampoViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.net.*

class MainActivity : ComponentActivity() {
    private val vm: CampoViewModel by viewModel()
    private val conectividad by lazy { getSystemService(ConnectivityManager::class.java) }
    private var registrada=false
    private val callback=object: ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) runOnUiThread { vm.sync() }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgroCampo(vm) }
    }
    override fun onStart() { super.onStart(); conectividad.registerDefaultNetworkCallback(callback); registrada=true }
    override fun onStop() { if(registrada) conectividad.unregisterNetworkCallback(callback); registrada=false; super.onStop() }
}
