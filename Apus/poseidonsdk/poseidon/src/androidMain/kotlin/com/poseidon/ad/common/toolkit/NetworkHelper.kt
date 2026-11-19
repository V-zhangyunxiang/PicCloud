package com.poseidon.ad.common.toolkit

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.poseidon.ad.api.PoseidonSetupProcessor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "NetworkHelper"

/**
 * 网络状态枚举。
 */
private enum class NetworkStatus {
    OFFLINE,
    WIFI,
    CELLULAR;

    val isOnline: Boolean
        get() = this != OFFLINE
}

/**
 * 当前网络快照。
 */
private data class NetworkSnapshot(
    val status: NetworkStatus
)

/**
 * 网络运行时依赖。
 *
 * Keep this free of heavy static initialization. It is first touched from
 * Application.onCreate through Poseidon setup, so all framework registration and
 * initial connectivity reads are moved onto the shared Poseidon worker.
 */
private object NetworkRuntime {
    private val registered = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun collectConnectivity(onChanged: (Boolean) -> Unit) {
        callbacks.add(onChanged)
        dispatchToBackground {
            ensureRegistered()
            notifyConnectivity(snapshot().status.isOnline)
        }
    }

    fun snapshot(): NetworkSnapshot {
        val connectivityManager = connectivityManager()
            ?: return NetworkSnapshot(NetworkStatus.OFFLINE)
        val network = connectivityManager.activeNetwork
            ?: return NetworkSnapshot(NetworkStatus.OFFLINE)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkSnapshot(NetworkStatus.OFFLINE)
        return NetworkSnapshot(capabilities.toStatus())
    }

    private fun ensureRegistered() {
        if (!registered.compareAndSet(false, true)) return
        val connectivityManager = connectivityManager()
        if (connectivityManager == null) {
            logInfo(TAG) { "ConnectivityManager unavailable; network observation disabled" }
            return
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        notifyConnectivity(true)
                    }

                    override fun onLost(network: Network) {
                        notifyConnectivity(snapshot().status.isOnline)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        notifyConnectivity(networkCapabilities.toStatus().isOnline)
                    }
                }
            )
        }.onFailure { throwable ->
            registered.set(false)
            logInfo(TAG) { "Network callback registration failed: ${throwable.message}" }
        }
    }

    private fun notifyConnectivity(isConnected: Boolean) {
        logInfo(TAG) { "Network changes observed, isConnected=$isConnected" }
        callbacks.forEach { callback ->
            runCatching { callback(isConnected) }
                .onFailure { throwable ->
                    logInfo(TAG) { "Network callback failed: ${throwable.message}" }
                }
        }
    }

    private fun connectivityManager(): ConnectivityManager? {
        val context = PoseidonSetupProcessor.getInstance().application.applicationContext
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
}

private fun NetworkCapabilities.toStatus(): NetworkStatus = when {
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkStatus.WIFI
    else -> NetworkStatus.OFFLINE
}

/**
 * 判断当前是否具备网络连接能力。
 */
fun isNetworkAvailable(): Boolean = NetworkRuntime.snapshot().status.isOnline

/**
 * 监听网络连通性变化。
 */
internal fun watchNetworkConnection(callback: (isConnected: Boolean) -> Unit) {
    NetworkRuntime.collectConnectivity(callback)
}
