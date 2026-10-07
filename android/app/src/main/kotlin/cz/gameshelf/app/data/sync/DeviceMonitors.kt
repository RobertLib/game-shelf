package cz.gameshelf.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has a network, from the default-network callback (`ACCESS_NETWORK_STATE`). */
class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isConnected = MutableStateFlow(connectivityManager.activeNetwork != null)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isConnected.value = true
                }

                override fun onLost(network: Network) {
                    _isConnected.value = false
                }
            },
        )
    }
}

/** Whether any activity of the app is started. Create on the main thread. */
class ForegroundMonitor(lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle) {
    private val _isForeground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _isForeground.value = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    _isForeground.value = false
                }
            },
        )
    }
}
