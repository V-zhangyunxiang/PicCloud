package com.poseidon.ad.common.toolkit

/** 可取消操作接口 */
fun interface Cancelable {
    /** 取消当前操作 */
    fun cancel()

    companion object {
        /** 提供一个空实现，避免重复创建 */
        val EMPTY: Cancelable
            get() = NoOpCancelable
    }
}

/** 空操作实现，确保 cancel 调用安全 */
private object NoOpCancelable : Cancelable {
    override fun cancel() {
        // no-op
    }
}

/** 可空对象的安全取消扩展 */
fun Cancelable?.cancelSafely() {
    this?.cancel()
}
