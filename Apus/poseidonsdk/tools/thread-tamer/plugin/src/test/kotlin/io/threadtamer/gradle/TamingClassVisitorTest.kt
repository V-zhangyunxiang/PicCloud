package io.threadtamer.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode

class TamingClassVisitorTest {
    @Test
    fun `route document keeps full active surface under the new schema`() {
        val document = File("src/main/resources/io/threadtamer/gradle/thread-tamer-routes.json").readText()
        val compiled = RouteBookCompiler.compile(document)
        assertTrue(compiled.active)
        assertEquals(31, compiled.callsByOwner.values.sumOf { it.size })
        assertEquals("io/threadtamer/runtime/ConcurrencyInterceptors", compiled.bridgeOwner)
        listOf("hookClass", "rewrites", "sourceOwner", "sourceMethod", "targetMethod").forEach { legacyKey ->
            assertFalse(document.contains("\"$legacyKey\""))
        }
    }

    @Test
    fun `plugin artifact exposes its packaged route table`() {
        val routeStream = ThreadTamerPlugin::class.java.getResourceAsStream(
            "/io/threadtamer/gradle/thread-tamer-routes.json"
        )
        assertTrue(routeStream != null)
        routeStream!!.bufferedReader().use { reader ->
            assertEquals(31, RouteBookCompiler.compile(reader.readText()).callsByOwner.values.sumOf { it.size })
        }
    }

    @Test
    fun `constructor allocation becomes bridge factory call`() {
        val output = weave(sampleClass(::emitThreadConstructor), constructorRoutes())
        val method = readMethod(output, "make")
        assertFalse(method.instructions.any { it is TypeInsnNode && it.opcode == Opcodes.NEW && it.desc == "java/lang/Thread" })
        assertTrue(method.instructions.any {
            it is MethodInsnNode && it.opcode == Opcodes.INVOKESTATIC &&
                it.owner == "io/threadtamer/runtime/ConcurrencyInterceptors" && it.name == "forgeThread"
        })
    }

    @Test
    fun `virtual start receives thread as static bridge argument`() {
        val output = weave(sampleClass(::emitThreadStart), startRoutes())
        val call = readMethod(output, "make").instructions.filterIsInstance<MethodInsnNode>()
            .single { it.owner == "io/threadtamer/runtime/ConcurrencyInterceptors" }
        assertEquals(Opcodes.INVOKESTATIC, call.opcode)
        assertEquals("ignite", call.name)
        assertEquals("(Ljava/lang/Thread;)V", call.desc)
    }

    private fun constructorRoutes() = CompiledRouteBook(
        active = true,
        bridgeOwner = "io/threadtamer/runtime/ConcurrencyInterceptors",
        callsByOwner = emptyMap(),
        constructorsByOwner = mapOf(
            "java/lang/Thread" to listOf(
                CompiledRoute(
                    owner = "java/lang/Thread",
                    method = "<init>",
                    descriptor = "()V",
                    opcode = Opcodes.INVOKESPECIAL,
                    afterCall = false,
                    bridgeMethod = "forgeThread",
                    bridgeDescriptor = "()Ljava/lang/Thread;"
                )
            )
        )
    )

    private fun startRoutes() = CompiledRouteBook(
        active = true,
        bridgeOwner = "io/threadtamer/runtime/ConcurrencyInterceptors",
        callsByOwner = mapOf(
            "java/lang/Thread" to listOf(
                CompiledRoute(
                    owner = "java/lang/Thread",
                    method = "start",
                    descriptor = "()V",
                    opcode = Opcodes.INVOKEVIRTUAL,
                    afterCall = false,
                    bridgeMethod = "ignite",
                    bridgeDescriptor = "(Ljava/lang/Thread;)V"
                )
            )
        ),
        constructorsByOwner = emptyMap()
    )

    private fun sampleClass(body: (MethodVisitor) -> Unit): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/Sample", null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "make", "()Ljava/lang/Thread;", null, null).also { method ->
            method.visitCode()
            body(method)
            method.visitMaxs(0, 0)
            method.visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun emitThreadConstructor(method: MethodVisitor) {
        method.visitTypeInsn(Opcodes.NEW, "java/lang/Thread")
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Thread", "<init>", "()V", false)
        method.visitInsn(Opcodes.ARETURN)
    }

    private fun emitThreadStart(method: MethodVisitor) {
        method.visitTypeInsn(Opcodes.NEW, "java/lang/Thread")
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Thread", "<init>", "()V", false)
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "start", "()V", false)
        method.visitInsn(Opcodes.ARETURN)
    }

    private fun weave(input: ByteArray, routes: CompiledRouteBook): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        ClassReader(input).accept(TamingClassVisitor(Opcodes.ASM9, writer, routes), 0)
        return writer.toByteArray()
    }

    private fun readMethod(bytes: ByteArray, methodName: String): org.objectweb.asm.tree.MethodNode {
        val node = ClassNode()
        ClassReader(bytes).accept(node, 0)
        return node.methods.single { it.name == methodName && Type.getReturnType(it.desc).className == "java.lang.Thread" }
    }
}
