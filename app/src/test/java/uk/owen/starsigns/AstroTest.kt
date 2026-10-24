package uk.owen.starsigns
import org.junit.Test
class AstroTest {
    @Test fun dump() {
        // 2026-10-22 20:00 UTC, near Glasgow
        val ms = java.time.Instant.parse("2026-10-22T20:00:00Z").toEpochMilli()
        val jd = Astro.julianDay(ms)
        val lst = Astro.localSiderealTime(jd, -4.25)
        println("JD=$jd LST(h)=${lst/15}")
        for (b in Astro.bodies(jd)) {
            val h = Astro.toHorizontal(b.pos, lst, 55.86)
            println("%-8s RA=%6.2fh Dec=%6.1f  alt=%5.1f az=%5.1f".format(b.name, b.pos.ra/15, b.pos.dec, h.alt, h.az))
        }
        for (c in Catalog.constellations) {
            val s = c.stars.minBy { it.mag }
            val h = Astro.toHorizontal(Astro.precess(s.ra, s.dec, jd), lst, 55.86)
            println("%-12s %-14s alt=%5.1f az=%5.1f".format(c.name, s.name, h.alt, h.az))
        }
    }
}
