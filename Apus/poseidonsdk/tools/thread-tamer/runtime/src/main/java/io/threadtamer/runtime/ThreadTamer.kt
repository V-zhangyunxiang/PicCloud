package io.threadtamer.runtime

/** Public host entry point. The artifact has no dependency on an advertising SDK. */
object ThreadTamer {
    @JvmStatic
    @JvmOverloads
    fun install(limits: ConcurrencyLimits = ConcurrencyLimits()) {
        ConcurrencyEngine.activate(limits)
    }

    @JvmStatic
    fun uninstall() {
        ConcurrencyEngine.deactivate()
    }

    @JvmStatic
    fun snapshot(): ConcurrencySnapshot = ConcurrencyEngine.readSnapshot()
}
