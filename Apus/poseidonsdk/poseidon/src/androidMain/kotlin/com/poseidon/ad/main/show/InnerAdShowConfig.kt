package com.poseidon.ad.main.show

class InnerAdShowConfig private constructor(
    val sceneName: String,
) {
    override fun toString() = "InnerAdShowConfig(sceneName=$sceneName)"
    override fun equals(other: Any?) = other is InnerAdShowConfig
            && sceneName == other.sceneName
    override fun hashCode() = sceneName.hashCode()

    class Builder {
        @set:JvmSynthetic // Hide 'void' setter from Java
        var sceneName: String? = null

        fun setSceneName(sceneName: String?) = apply { this.sceneName = sceneName }

        fun build() = InnerAdShowConfig(sceneName!!)
    }
}

@JvmSynthetic // Hide from Java callers who should use Builder.
fun getAdShowConfig(initializer: InnerAdShowConfig.Builder.() -> Unit): InnerAdShowConfig {
    return InnerAdShowConfig.Builder().apply(initializer).build()
}