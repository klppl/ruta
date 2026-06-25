package io.github.klppl.ruta.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Parser coverage for the procedural-cosmetic chain (and that we don't regress plain rules). */
class AbpParserTest {

    private fun parse(vararg lines: String): AbpParser.Accumulator {
        val acc = AbpParser.Accumulator()
        AbpParser.parseInto(lines.joinToString("\n"), acc)
        return acc
    }

    @Test fun plainCosmetic_isNotProcedural() {
        val acc = parse("""twitter.com,x.com##[aria-label="Get Verified"]""")
        assertTrue(acc.domainHide["twitter.com"]!!.contains("""[aria-label="Get Verified"]"""))
        assertTrue(acc.domainHide["x.com"]!!.contains("""[aria-label="Get Verified"]"""))
        assertTrue(acc.proceduralDomain.isEmpty())
    }

    @Test fun nativeHas_staysPlain() {
        // `:has()` is native CSS, not a procedural operator — must remain a plain hide rule.
        val acc = parse("example.com##div:has(.ad)")
        assertTrue(acc.domainHide["example.com"]!!.contains("div:has(.ad)"))
        assertTrue(acc.proceduralDomain.isEmpty())
    }

    @Test fun hasText_parsesBaseAndNeedle() {
        val acc = parse("twitter.com,x.com###layers>div:last-of-type:has-text(Switch to the app)")
        val rule = acc.proceduralDomain["x.com"]!!.single()
        assertEquals("#layers>div:last-of-type", rule.selector)
        assertEquals(listOf(ProceduralStep("has-text", "Switch to the app")), rule.steps)
    }

    @Test fun hasTextThenUpward_chains() {
        val acc = parse("www.reddit.com##header button:has-text(/Shop Avatars/):upward(1)")
        val rule = acc.proceduralDomain["www.reddit.com"]!!.single()
        assertEquals("header button", rule.selector)
        assertEquals(
            listOf(ProceduralStep("has-text", "/Shop Avatars/"), ProceduralStep("upward", "1")),
            rule.steps,
        )
    }

    @Test fun upwardBySelector_parses() {
        val acc = parse("""reddit.com##[href="/user/AutoModerator/"]:upward(shreddit-comment)""")
        val rule = acc.proceduralDomain["reddit.com"]!!.single()
        assertEquals("""[href="/user/AutoModerator/"]""", rule.selector)
        assertEquals(listOf(ProceduralStep("upward", "shreddit-comment")), rule.steps)
    }

    @Test fun style_keepsHasBaseAndCss() {
        val sel = """:is([aria-posinset],[aria-describedby]:not([aria-posinset])):has([aria-label="Reels"])"""
        val acc = parse("""www.facebook.com##$sel:style(height: 0 !important; overflow: hidden !important;)""")
        val rule = acc.proceduralDomain["www.facebook.com"]!!.single()
        assertEquals(sel, rule.selector)
        assertEquals(
            listOf(ProceduralStep("style", "height: 0 !important; overflow: hidden !important;")),
            rule.steps,
        )
    }

    @Test fun balancedParens_inHasTextRegex() {
        val acc = parse("example.com##a:has-text(/Top (live|broad)/):upward(7)")
        val rule = acc.proceduralDomain["example.com"]!!.single()
        assertEquals("a", rule.selector)
        assertEquals(
            listOf(ProceduralStep("has-text", "/Top (live|broad)/"), ProceduralStep("upward", "7")),
            rule.steps,
        )
    }

    @Test fun unsupportedProcedural_isDropped() {
        val acc = parse(
            "example.com##div:matches-css(display: block)",
            "example.com##p:xpath(//div)",
            "example.com#\$#json-prune a",
            "example.com##div:min-text-length(20)",
        )
        assertTrue(acc.proceduralDomain.isEmpty())
        assertNull(acc.domainHide["example.com"])
    }

    @Test fun networkAndPlainStillWork() {
        val acc = parse("||ads.example.com^", "@@||good.example.com^", "##.generic-ad")
        assertTrue(acc.blocked.contains("ads.example.com"))
        assertTrue(acc.allowed.contains("good.example.com"))
        assertTrue(acc.genericHide.contains(".generic-ad"))
    }

    /** The shipped list must fully parse into the rules we expect (guards against typos). */
    @Test fun bundledRutaList_parses() {
        val file = File("src/main/assets/filters/ruta.txt")
        if (!file.exists()) return // skip if run from a different working dir
        val acc = AbpParser.Accumulator()
        AbpParser.parseInto(file.readText(), acc)

        // Meta install-nag (procedural, x-domain)
        assertTrue(acc.proceduralDomain["threads.com"]!!.any { it.steps.first().op == "has-text" })
        // X "Switch to the app"
        assertTrue(
            acc.proceduralDomain["x.com"]!!.any {
                it.selector == "#layers>div:last-of-type" &&
                    it.steps == listOf(ProceduralStep("has-text", "Switch to the app"))
            },
        )
        // Reddit AutoModerator upward rule
        assertTrue(acc.proceduralDomain["reddit.com"]!!.any { it.steps.any { s -> s.op == "upward" } })
        // Facebook :style rule
        assertTrue(acc.proceduralDomain["www.facebook.com"]!!.any { it.steps.any { s -> s.op == "style" } })
        // Plain X cosmetic survived
        assertTrue(acc.domainHide["x.com"]!!.contains("""[aria-label="Get Verified"]"""))
        // Nothing leaked an unparsed procedural string into plain hide sets
        assertFalse(acc.genericHide.any { it.contains(":has-text(") || it.contains(":upward(") })
    }
}
