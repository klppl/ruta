package com.ruta.blocking

import com.ruta.util.Hosts

/** Immutable result of parsing element-hiding rules. */
class CosmeticRules(
    private val genericHide: Set<String>,
    private val genericUnhide: Set<String>,
    private val domainHide: Map<String, Set<String>>,
    private val domainUnhide: Map<String, Set<String>>,
) {
    /**
     * Selectors to hide on [host]: all generic hide rules plus any whose positive domain
     * restriction matches the host, minus generic and domain-specific unhide exceptions.
     */
    fun selectorsFor(host: String): List<String> {
        if (host.isEmpty()) return genericHide.toList()
        val out = LinkedHashSet<String>(genericHide.size + 64)
        out.addAll(genericHide)
        Hosts.forEachSuffix(host) { suffix ->
            domainHide[suffix]?.let(out::addAll)
            false
        }
        if (genericUnhide.isNotEmpty()) out.removeAll(genericUnhide)
        Hosts.forEachSuffix(host) { suffix ->
            domainUnhide[suffix]?.let(out::removeAll)
            false
        }
        return out.toList()
    }

    val isEmpty: Boolean get() = genericHide.isEmpty() && domainHide.isEmpty()

    companion object {
        val EMPTY = CosmeticRules(emptySet(), emptySet(), emptyMap(), emptyMap())
    }
}
