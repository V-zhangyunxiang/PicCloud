package io.threadtamer.gradle

import com.google.gson.Gson
import org.objectweb.asm.Opcodes

internal data class RouteBook(
    val schema: Int = 1,
    val active: Boolean = true,
    val bridgeType: String = "io.threadtamer.runtime.ConcurrencyInterceptors",
    val operations: List<RouteOperation> = emptyList()
)

internal data class RouteOperation(
    val active: Boolean = true,
    val origin: OriginCall = OriginCall(),
    val redirect: RedirectCall = RedirectCall(),
    val afterCall: Boolean = false
)

internal data class OriginCall(
    val type: String = "",
    val name: String = "",
    val descriptor: String = "",
    val dispatch: String? = null
)

internal data class RedirectCall(
    val name: String = "",
    val descriptor: String? = null
)

internal data class CompiledRouteBook(
    val active: Boolean,
    val bridgeOwner: String,
    val callsByOwner: Map<String, List<CompiledRoute>>,
    val constructorsByOwner: Map<String, List<CompiledRoute>>
)

internal data class CompiledRoute(
    val owner: String,
    val method: String,
    val descriptor: String,
    val opcode: Int?,
    val afterCall: Boolean,
    val bridgeMethod: String,
    val bridgeDescriptor: String
)

internal object RouteBookCompiler {
    private val decoder = Gson()

    fun compile(document: String): CompiledRouteBook {
        val parsed = decoder.fromJson(document, RouteBook::class.java) ?: RouteBook(active = false)
        val routes = parsed.operations.asSequence()
            .filter { it.active }
            .mapNotNull(::compileOperation)
            .toList()
        return CompiledRouteBook(
            active = parsed.active,
            bridgeOwner = parsed.bridgeType.trim().replace('.', '/'),
            callsByOwner = routes.groupBy { it.owner },
            constructorsByOwner = routes.filter {
                it.method == "<init>" && it.opcode == Opcodes.INVOKESPECIAL
            }.groupBy { it.owner }
        )
    }

    private fun compileOperation(entry: RouteOperation): CompiledRoute? {
        val owner = entry.origin.type.trim().replace('.', '/').takeIf(String::isNotEmpty) ?: return null
        val method = entry.origin.name.trim().takeIf(String::isNotEmpty) ?: return null
        val descriptor = entry.origin.descriptor.trim().takeIf(String::isNotEmpty) ?: return null
        val bridgeMethod = entry.redirect.name.trim().takeIf(String::isNotEmpty) ?: return null
        return CompiledRoute(
            owner = owner,
            method = method,
            descriptor = descriptor,
            opcode = when (entry.origin.dispatch?.uppercase()) {
                null, "" -> null
                "VIRTUAL" -> Opcodes.INVOKEVIRTUAL
                "STATIC" -> Opcodes.INVOKESTATIC
                "INTERFACE" -> Opcodes.INVOKEINTERFACE
                "SPECIAL" -> Opcodes.INVOKESPECIAL
                else -> return null
            },
            afterCall = entry.afterCall,
            bridgeMethod = bridgeMethod,
            bridgeDescriptor = entry.redirect.descriptor?.trim().takeUnless { it.isNullOrEmpty() } ?: descriptor
        )
    }
}
