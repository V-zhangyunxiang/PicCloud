package io.threadtamer.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

interface TamingParameters : InstrumentationParameters {
    @get:Input val active: Property<Boolean>
    @get:Input val ignoredPrefixes: ListProperty<String>
    @get:Input val routeDocument: Property<String>
}

abstract class TamingVisitorFactory : AsmClassVisitorFactory<TamingParameters> {
    override fun createClassVisitor(context: ClassContext, downstream: ClassVisitor): ClassVisitor {
        val compiled = RouteBookCompiler.compile(parameters.get().routeDocument.get())
        return TamingClassVisitor(Opcodes.ASM9, downstream, compiled)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val inputs = parameters.get()
        if (!inputs.active.getOrElse(true)) return false
        val dottedName = classData.className
        if (dottedName.endsWith(".R") || dottedName.contains(".R$") || dottedName.endsWith(".BuildConfig")) {
            return false
        }
        val compiled = RouteBookCompiler.compile(inputs.routeDocument.get())
        if (!compiled.active || dottedName.replace('.', '/') == compiled.bridgeOwner) return false
        return inputs.ignoredPrefixes.getOrElse(emptyList()).none { dottedName.startsWith(it.replace('/', '.')) }
    }
}
