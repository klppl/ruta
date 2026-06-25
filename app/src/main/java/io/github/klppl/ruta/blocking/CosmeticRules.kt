package io.github.klppl.ruta.blocking

import io.github.klppl.ruta.util.Hosts

/**
 * A procedural element-hiding rule: hide elements matching [selector] whose text content matches
 * [text]. [text] is either a plain substring or a `/regex/flags` literal — content.js decides.
 * This is the subset of uBlock's `:has-text()` / `:contains()` we can evaluate page-side.
 */
data class ProceduralRule(
    val selector: String,
    val text: String,
)

/** Immutable result of parsing element-hiding rules. */
class CosmeticRules(
    private val genericHide: Set<String>,
    private val genericUnhide: Set<String>,
    private val domainHide: Map<String, Set<String>>,
    private val domainUnhide: Map<String, Set<String>>,
    private val proceduralGeneric: Set<ProceduralRule> = emptySet(),
    private val proceduralDomain: Map<String, Set<ProceduralRule>> = emptyMap(),
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

    /** Procedural (`:has-text`) rules to evaluate on [host]: generic plus domain-matched. */
    fun proceduralFor(host: String): List<ProceduralRule> {
        if (proceduralGeneric.isEmpty() && proceduralDomain.isEmpty()) return emptyList()
        val out = LinkedHashSet<ProceduralRule>(proceduralGeneric)
        if (host.isNotEmpty()) {
            Hosts.forEachSuffix(host) { suffix ->
                proceduralDomain[suffix]?.let(out::addAll)
                false
            }
        }
        return out.toList()
    }

    val isEmpty: Boolean
        get() = genericHide.isEmpty() && domainHide.isEmpty() &&
            proceduralGeneric.isEmpty() && proceduralDomain.isEmpty()

    companion object {
        val EMPTY = CosmeticRules(emptySet(), emptySet(), emptyMap(), emptyMap())
    }
}
