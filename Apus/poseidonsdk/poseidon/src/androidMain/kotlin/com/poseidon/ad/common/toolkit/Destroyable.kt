package com.poseidon.ad.common.toolkit

/** 可销毁操作接口 */
fun interface Destroyable {
    /** 销毁当前资源 */
    fun destroy()

    companion object {
        /** 复用的空实现，避免重复分配 */
        val EMPTY: Destroyable
            get() = NoOpDestroyable
    }
}

/** 空操作 Destroyable，实现销毁无副作用 */
private object NoOpDestroyable : Destroyable {
    override fun destroy() {
        // no-op
    }
}

/** 可空销毁操作的安全扩展 */
fun Destroyable?.destroySafely() {
    this?.destroy()
}
