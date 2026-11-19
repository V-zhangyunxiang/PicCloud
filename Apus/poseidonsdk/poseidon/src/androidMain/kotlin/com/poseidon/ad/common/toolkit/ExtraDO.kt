package com.poseidon.ad.common.toolkit

/** 通用携带附加参数的数据对象 */
data class ExtraDO<T, E>(
    /** 业务主体数据 */
    val data: T,
    /** 附加信息或上下文 */
    val extra: E
) {
    companion object : ExtraStringFactory by ExtraFactoryDelegate
}

/** 针对字符串附加信息的创建契约 */
private interface ExtraStringFactory {
    /** 创建包含字符串 Extra 的数据对象 */
    fun <T> stringExtra(data: T, extra: String): ExtraDO<T, String>
}

/** 内部工厂，集中构建 ExtraDO */
private object ExtraFactoryDelegate : ExtraStringFactory {
    override fun <T> stringExtra(data: T, extra: String): ExtraDO<T, String> {
        return compose(data, extra)
    }

    /** 将数据与 Extra 组装成 ExtraDO */
    private fun <T, E> compose(data: T, extra: E): ExtraDO<T, E> = ExtraDO(data = data, extra = extra)
}
