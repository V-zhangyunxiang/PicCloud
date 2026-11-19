package io.threadtamer.gradle

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class ThreadTamerPlugin : Plugin<Project> {
    private companion object {
        const val PACKAGED_ROUTES = "/io/threadtamer/gradle/thread-tamer-routes.json"
    }

    override fun apply(target: Project) {
        val options = target.extensions.create("threadTamer", ThreadTamerExtension::class.java)
        target.plugins.withId("com.android.application") { attach(target, options, InstrumentationScope.ALL) }
        target.plugins.withId("com.android.library") { attach(target, options, InstrumentationScope.PROJECT) }
    }

    private fun attach(
        target: Project,
        options: ThreadTamerExtension,
        scope: InstrumentationScope
    ) {
        val androidComponents = target.extensions.findByType(AndroidComponentsExtension::class.java)
            ?: throw GradleException("Thread Tamer requires an Android application or library plugin")
        @Suppress("UNCHECKED_CAST")
        val typedComponents = androidComponents as AndroidComponentsExtension<*, *, *>
        typedComponents.onVariants(typedComponents.selector().all()) { variant ->
            if (!options.active.getOrElse(true)) return@onVariants
            val routeText = loadRouteText(options)
            variant.instrumentation.transformClassesWith(
                TamingVisitorFactory::class.java,
                scope
            ) { inputs ->
                inputs.active.set(options.active)
                inputs.ignoredPrefixes.set(options.ignoredTypePrefixes)
                inputs.routeDocument.set(routeText)
            }
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )
        }
    }

    private fun loadRouteText(options: ThreadTamerExtension): String {
        val overrideFile = options.ruleBook.orNull?.asFile
        if (overrideFile != null) {
            if (!overrideFile.isFile) {
                throw GradleException("Thread Tamer route file does not exist: ${overrideFile.absolutePath}")
            }
            return overrideFile.readText(Charsets.UTF_8)
        }
        return ThreadTamerPlugin::class.java.getResourceAsStream(PACKAGED_ROUTES)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw GradleException("Thread Tamer plugin artifact is missing $PACKAGED_ROUTES")
    }
}
