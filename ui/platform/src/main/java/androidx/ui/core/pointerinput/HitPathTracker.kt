/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.core.pointerinput

import androidx.ui.core.LayoutNode
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerInputChange
import androidx.ui.core.PointerInputNode
import androidx.ui.core.PxPosition
import androidx.ui.core.positionRelativeToAncestor
import androidx.ui.core.positionRelativeToRoot
import androidx.ui.core.isAttached
import androidx.ui.core.visitLayoutChildren

/**
 * Organizes pointers and the [PointerInputNode]s that they hit into a hierarchy such that
 * [PointerInputChange]s can be dispatched to the [PointerInputNode]s in a hierarchical fashion.
 */
internal class HitPathTracker {

    internal val root: Node = Node()

    /**
     * Associates [pointerId] to [pointerInputNodes] and tracks them.
     *
     * @param pointerId The id of the pointer that was hit tested against [PointerInputNode]s
     * @param pointerInputNodes The [PointerInputNode]s that were hit by [pointerId]
     */
    fun addHitPath(pointerId: Int, pointerInputNodes: List<PointerInputNode>) {
        var parent = root
        var merging = true
        eachPin@ for (pointerInputNode in pointerInputNodes) {
            if (merging) {
                val node = parent.children.find { it.pointerInputNode == pointerInputNode }
                if (node != null) {
                    node.pointerIds.add(pointerId)
                    parent = node
                    continue@eachPin
                } else {
                    merging = false
                }
            }
            val node = Node(pointerInputNode).apply {
                pointerIds.add(pointerId)
            }
            parent.children.add(node)
            parent = node
        }
    }

    /**
     * Dispatches [pointerInputChanges] through the hierarchy; first down the hierarchy, passing
     * [downPass] to each [PointerInputNode], and then up the hierarchy with [upPass] if [upPass]
     * is not null.
     */
    fun dispatchChanges(
        pointerInputChanges: List<PointerInputChange>,
        downPass: PointerEventPass,
        upPass: PointerEventPass? = null
    ): List<PointerInputChange> {

        // TODO(b/124523868): PointerInputNodes should opt into passes prevent having to visit
        // each one for every PointerInputChange.

        val idToChangesMap = pointerInputChanges.associateTo(mutableMapOf()) {
            it.id to it
        }
        root.dispatchChanges(idToChangesMap, downPass, upPass)
        return idToChangesMap.values.toList()
    }

    /**
     * Removes [PointerInputNode]s that have been removed from the component hierarchy.
     */
    fun removeDetachedPointerInputNodes() {
        root.removeDetachedPointerInputNodes()
    }

    /**
     * Removes the [pointerId] and any [PointerInputNode]s that are no longer associated with any
     * remaining [pointerId].
     */
    fun removePointerId(pointerId: Int) {
        root.removePointerId(pointerId)
    }

    /**
     * Updates this [HitPathTracker]'s cached knowledge of the bounds of the [PointerInputNode]s
     * it is tracking.  This is is necessary to call before calls to [dispatchChanges] so that
     * the positions of [PointerInputChange]s are offset to be relative to the [PointerInputNode]s
     * that are going to receive them.
     */
    fun refreshOffsets() {
        root.refreshOffsets()
    }
}

// TODO(shepshapard): This really should be private. Currently some tests inspect the node's
// directly which is unnecessary and bad practice.
internal class Node(
    val pointerInputNode: PointerInputNode? = null,
    val layoutNode: LayoutNode? = null
) {
    val pointerIds: MutableSet<Int> = mutableSetOf()
    val children: MutableSet<Node> = mutableSetOf()

    // Stores the associated PointerInputNode's virtual position relative to it's parent
    // PointerInputNode, or relative to the crane root if it has no parent PointerInputNode.
    var offset: PxPosition = PxPosition.Origin

    fun dispatchChanges(
        pointerInputChanges: MutableMap<Int, PointerInputChange>,
        downPass: PointerEventPass,
        upPass: PointerEventPass?
    ) {
        // Filter for changes that are associated with pointer ids that are relevant to this node.
        val relevantChanges = if (pointerInputNode == null) {
            pointerInputChanges
        } else {
            pointerInputChanges.filterTo(mutableMapOf()) { entry ->
                pointerIds.contains(entry.key)
            }
        }

        // For each relevant change:
        //  1. subtract the offset
        //  2. dispatch the change on the down pass,
        //  3. update it in relevantChanges.
        if (pointerInputNode != null) {
            relevantChanges.run {
                subtractOffset(offset)
                dispatchToPointerInputNode(pointerInputNode, downPass)
            }
        }

        // Call children recursively with the relevant changes.
        children.forEach { it.dispatchChanges(relevantChanges, downPass, upPass) }

        // For each relevant change:
        //  1. dispatch the change on the up pass,
        //  2. add the offset,
        //  3. update it in  relevant changes.
        if (pointerInputNode != null && upPass != null) {
            relevantChanges.run {
                dispatchToPointerInputNode(pointerInputNode, upPass)
                addOffset(offset)
            }
        }

        // Mutate the pointerInputChanges with the ones we modified.
        pointerInputChanges.putAll(relevantChanges)
    }

    fun removeDetachedPointerInputNodes() {
        children.removeAll {
            it.pointerInputNode != null && !it.pointerInputNode.isAttached()
        }
        children.forEach {
            it.removeDetachedPointerInputNodes()
        }
    }

    fun removePointerId(pointerId: Int) {
        children.forEach {
            it.pointerIds.remove(pointerId)
        }
        children.removeAll {
            it.pointerInputNode != null && it.pointerIds.isEmpty()
        }
        children.forEach {
            it.removePointerId(pointerId)
        }
    }

    fun refreshOffsets() {
        if (pointerInputNode == null) {
            children.forEach { child ->
                child.offset = findLastLayoutNode(child)?.positionRelativeToRoot()
                    ?: PxPosition.Origin
            }
        } else {
            children.forEach { child ->
                val layoutNode = findLastLayoutNode(child)
                val myLayoutNode = findLastLayoutNode(this)
                child.offset = layoutNode?.positionRelativeToAncestor(myLayoutNode!!)
                    ?: PxPosition.Origin
            }
        }
        children.forEach { child ->
            child.refreshOffsets()
        }
    }

    private fun findLastLayoutNode(node: Node): LayoutNode? {
        var layoutNode: LayoutNode? = null
        node.pointerInputNode?.visitLayoutChildren { child ->
            layoutNode = child
        }
        return layoutNode
    }

    override fun toString(): String {
        return "Node(pointerInputNode=$pointerInputNode, children=$children, " +
                "pointerIds=$pointerIds)"
    }

    private fun MutableMap<Int, PointerInputChange>.dispatchToPointerInputNode(
        node: PointerInputNode,
        pass: PointerEventPass
    ) {
        node.pointerInputHandler(values.toList(), pass).forEach {
            this[it.id] = it
        }
    }

    private fun MutableMap<Int, PointerInputChange>.addOffset(pxPosition: PxPosition) {
        if (pxPosition != PxPosition.Origin) {
            replaceEverything {
                it.copy(
                    current = it.current.copy(position = it.current.position?.plus(pxPosition)),
                    previous = it.previous.copy(position = it.previous.position?.plus(pxPosition))
                )
            }
        }
    }

    private fun MutableMap<Int, PointerInputChange>.subtractOffset(pxPosition: PxPosition) {
        addOffset(-pxPosition)
    }

    private inline fun <K, V> MutableMap<K, V>.replaceEverything(f: (V) -> V) {
        for (entry in this) {
            entry.setValue(f(entry.value))
        }
    }
}
