package io.threadtamer.gradle

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

internal class TamingClassVisitor(
    apiLevel: Int,
    downstream: ClassVisitor,
    private val routes: CompiledRouteBook
) : ClassVisitor(apiLevel, downstream) {
    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val destination = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (name == "<clinit>") return destination
        return RoutingMethodNode(api, access, name, descriptor, signature, exceptions, destination, routes)
    }

    private class RoutingMethodNode(
        apiLevel: Int,
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
        private val destination: MethodVisitor,
        private val routes: CompiledRouteBook
    ) : MethodNode(apiLevel, access, name, descriptor, signature, exceptions) {
        override fun visitEnd() {
            redirectCalls()
            accept(destination)
        }

        private fun redirectCalls() {
            val pendingNews = mutableListOf<PendingAllocation>()
            var cursor = instructions.first
            while (cursor != null) {
                val following = cursor.next
                val newest = pendingNews.lastOrNull()
                when (cursor) {
                    is TypeInsnNode -> {
                        newest?.acceptingDuplicates = false
                        if (cursor.opcode == Opcodes.NEW && routes.constructorsByOwner.containsKey(cursor.desc)) {
                            pendingNews += PendingAllocation(cursor.desc, cursor)
                        }
                    }
                    is InsnNode -> {
                        if (cursor.opcode == Opcodes.DUP && newest != null && newest.acceptingDuplicates && !newest.unsafe) {
                            newest.duplicates += cursor
                        } else if (newest != null) {
                            newest.acceptingDuplicates = false
                            if (unsafeStackOpcode(cursor.opcode)) newest.unsafe = true
                        }
                    }
                    is VarInsnNode -> if (newest != null) {
                        newest.acceptingDuplicates = false
                        if (cursor.opcode in Opcodes.ISTORE..Opcodes.ASTORE) newest.unsafe = true
                    }
                    is MethodInsnNode -> {
                        if (newest != null && cursor.name != "<init>") newest.acceptingDuplicates = false
                        redirect(cursor, pendingNews)
                    }
                    else -> newest?.let { it.acceptingDuplicates = false }
                }
                cursor = following
            }
        }

        private fun redirect(call: MethodInsnNode, pendingNews: MutableList<PendingAllocation>) {
            val route = if (call.name == "<init>") {
                routes.constructorsByOwner[call.owner]?.firstOrNull { it.descriptor == call.desc }
            } else {
                routes.callsByOwner[call.owner]?.firstOrNull {
                    it.method == call.name && it.descriptor == call.desc && (it.opcode == null || it.opcode == call.opcode)
                }
            } ?: run {
                if (call.name == "<init>") removePending(pendingNews, call.owner)
                return
            }

            if (route.afterCall) {
                instructions.insert(call, MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    routes.bridgeOwner,
                    route.bridgeMethod,
                    route.bridgeDescriptor,
                    false
                ))
                return
            }

            var restoredCopies = 0
            if (call.name == "<init>") {
                val allocation = removePending(pendingNews, call.owner) ?: return
                if (allocation.unsafe || allocation.duplicates.isEmpty()) return
                restoredCopies = (allocation.duplicates.size - 1).coerceAtLeast(0)
                instructions.remove(allocation.newInstruction)
                allocation.duplicates.forEach(instructions::remove)
            }
            val bridgeCall = MethodInsnNode(
                Opcodes.INVOKESTATIC,
                routes.bridgeOwner,
                route.bridgeMethod,
                route.bridgeDescriptor,
                false
            )
            instructions.set(call, bridgeCall)
            repeat(restoredCopies) { instructions.insert(bridgeCall, InsnNode(Opcodes.DUP)) }
        }

        private fun removePending(entries: MutableList<PendingAllocation>, owner: String): PendingAllocation? {
            for (slot in entries.lastIndex downTo 0) {
                if (entries[slot].owner == owner) return entries.removeAt(slot)
            }
            return null
        }

        private fun unsafeStackOpcode(opcode: Int): Boolean = opcode == Opcodes.SWAP ||
            opcode == Opcodes.DUP_X1 || opcode == Opcodes.DUP_X2 || opcode == Opcodes.DUP2 ||
            opcode == Opcodes.DUP2_X1 || opcode == Opcodes.DUP2_X2 || opcode == Opcodes.POP || opcode == Opcodes.POP2

        private data class PendingAllocation(
            val owner: String,
            val newInstruction: TypeInsnNode,
            val duplicates: MutableList<AbstractInsnNode> = mutableListOf(),
            var acceptingDuplicates: Boolean = true,
            var unsafe: Boolean = false
        )
    }
}
