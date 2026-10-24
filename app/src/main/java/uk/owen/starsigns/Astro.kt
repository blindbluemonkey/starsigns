package uk.owen.starsigns

import kotlin.math.*

/** Equatorial position of date. ra in degrees, dec in degrees. */
data class Equatorial(val ra: Double, val dec: Double)

/** Horizontal position. Both in degrees; azimuth measured from true north through east. */
data class Horizontal(val alt: Double, val az: Double)

data class Body(val name: String, val pos: Equatorial, val color: Int, val size: Float)

object Astro {
    private const val D2R = PI / 180.0
    private const val R2D = 180.0 / PI

    fun julianDay(millis: Long): Double = millis / 86_400_000.0 + 2440587.5

    private fun norm360(x: Double): Double { val r = x % 360.0; return if (r < 0) r + 360.0 else r }

    /** Local sidereal time in degrees. */
    fun localSiderealTime(jd: Double, lonDeg: Double): Double {
        val d = jd - 2451545.0
        val t = d / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38710000.0
        return norm360(gmst + lonDeg)
    }

    /** Simple annual precession from J2000 to the date (good to well under 0.1° for decades). */
    fun precess(raHours: Double, decDeg: Double, jd: Double): Equatorial {
        val years = (jd - 2451545.0) / 365.25
        val ra = raHours * 15.0
        val m = 46.1244 / 3600.0      // deg/yr
        val n = 20.0431 / 3600.0      // deg/yr
        val dra = (m + n * sin(ra * D2R) * tan(decDeg * D2R)) * years
        val ddec = n * cos(ra * D2R) * years
        return Equatorial(norm360(ra + dra), decDeg + ddec)
    }

    fun toHorizontal(eq: Equatorial, lst: Double, latDeg: Double): Horizontal {
        val ha = (lst - eq.ra) * D2R
        val dec = eq.dec * D2R
        val lat = latDeg * D2R
        val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val y = -sin(ha) * cos(dec)
        val x = cos(lat) * sin(dec) - sin(lat) * cos(dec) * cos(ha)
        val az = atan2(y, x)
        return Horizontal(alt * R2D, norm360(az * R2D))
    }

    fun obliquity(jd: Double): Double = 23.439291 - 0.0130042 * (jd - 2451545.0) / 36525.0

    fun eclipticToEquatorial(lonDeg: Double, latDeg: Double, eps: Double): Equatorial {
        val l = lonDeg * D2R; val b = latDeg * D2R; val e = eps * D2R
        val x = cos(b) * cos(l)
        val y = cos(b) * sin(l) * cos(e) - sin(b) * sin(e)
        val z = cos(b) * sin(l) * sin(e) + sin(b) * cos(e)
        return Equatorial(norm360(atan2(y, x) * R2D), asin(z.coerceIn(-1.0, 1.0)) * R2D)
    }

    /** Points along the ecliptic (the Sun's path – the zodiac band is centred on it). */
    fun eclipticPoints(jd: Double, stepDeg: Int = 4): List<Equatorial> {
        val eps = obliquity(jd)
        return (0 until 360 step stepDeg).map { eclipticToEquatorial(it.toDouble(), 0.0, eps) }
    }

    // ---------------- Sun, Moon & planets ----------------

    // JPL "Approximate positions of the planets" (valid 1800–2050), J2000 ecliptic.
    // a, e, I, L, longPeri, longNode   followed by rates per century.
    private class Elements(val v: DoubleArray, val r: DoubleArray)

    private val elements = mapOf(
        "Mercury" to Elements(
            doubleArrayOf(0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593),
            doubleArrayOf(0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081)),
        "Venus" to Elements(
            doubleArrayOf(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255),
            doubleArrayOf(0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418)),
        "Earth" to Elements(
            doubleArrayOf(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0),
            doubleArrayOf(0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0)),
        "Mars" to Elements(
            doubleArrayOf(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891),
            doubleArrayOf(0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343)),
        "Jupiter" to Elements(
            doubleArrayOf(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909),
            doubleArrayOf(-0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106)),
        "Saturn" to Elements(
            doubleArrayOf(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448),
            doubleArrayOf(-0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794)),
    )

    /** Heliocentric J2000 ecliptic rectangular coordinates (AU). */
    private fun helio(name: String, t: Double): DoubleArray {
        val el = elements.getValue(name)
        val a = el.v[0] + el.r[0] * t
        val e = el.v[1] + el.r[1] * t
        val i = (el.v[2] + el.r[2] * t) * D2R
        val l = el.v[3] + el.r[3] * t
        val wBar = el.v[4] + el.r[4] * t
        val node = el.v[5] + el.r[5] * t
        val w = (wBar - node) * D2R
        var m = norm360(l - wBar); if (m > 180) m -= 360.0
        val mr = m * D2R
        var ea = mr + e * sin(mr)
        repeat(8) { ea -= (ea - e * sin(ea) - mr) / (1 - e * cos(ea)) }
        val xp = a * (cos(ea) - e)
        val yp = a * sqrt(1 - e * e) * sin(ea)
        val o = node * D2R
        val x = (cos(w) * cos(o) - sin(w) * sin(o) * cos(i)) * xp + (-sin(w) * cos(o) - cos(w) * sin(o) * cos(i)) * yp
        val y = (cos(w) * sin(o) + sin(w) * cos(o) * cos(i)) * xp + (-sin(w) * sin(o) + cos(w) * cos(o) * cos(i)) * yp
        val z = (sin(w) * sin(i)) * xp + (cos(w) * sin(i)) * yp
        return doubleArrayOf(x, y, z)
    }

    private fun geoFromEcliptic(x: Double, y: Double, z: Double, jd: Double): Equatorial {
        val lon = atan2(y, x) * R2D
        val lat = atan2(z, sqrt(x * x + y * y)) * R2D
        // J2000 ecliptic -> J2000 equatorial, then precess to date like the stars.
        val eq = eclipticToEquatorial(lon, lat, 23.439291)
        return precess(eq.ra / 15.0, eq.dec, jd)
    }

    fun bodies(jd: Double): List<Body> {
        val t = (jd - 2451545.0) / 36525.0
        val earth = helio("Earth", t)
        val out = ArrayList<Body>()
        out += Body("Sun", geoFromEcliptic(-earth[0], -earth[1], -earth[2], jd), 0xFFFFD54F.toInt(), 14f)
        val styles = mapOf(
            "Mercury" to (0xFFB0BEC5.toInt() to 5f),
            "Venus" to (0xFFFFF8E1.toInt() to 8f),
            "Mars" to (0xFFFF7043.toInt() to 6f),
            "Jupiter" to (0xFFFFE0B2.toInt() to 8f),
            "Saturn" to (0xFFFFECB3.toInt() to 7f),
        )
        for ((name, style) in styles) {
            val p = helio(name, t)
            out += Body(name, geoFromEcliptic(p[0] - earth[0], p[1] - earth[1], p[2] - earth[2], jd), style.first, style.second)
        }
        out += Body("Moon", moon(jd), 0xFFECEFF1.toInt(), 13f)
        return out
    }

    /** Low-precision Moon (Astronomical Almanac), ~0.3°; ecliptic of date. */
    private fun moon(jd: Double): Equatorial {
        val t = (jd - 2451545.0) / 36525.0
        fun sd(x: Double) = sin(x * D2R)
        val lon = 218.32 + 481267.881 * t +
            6.29 * sd(135.0 + 477198.87 * t) - 1.27 * sd(259.3 - 413335.36 * t) +
            0.66 * sd(235.7 + 890534.22 * t) + 0.21 * sd(269.9 + 954397.74 * t) -
            0.19 * sd(357.5 + 35999.05 * t) - 0.11 * sd(186.5 + 966404.03 * t)
        val lat = 5.13 * sd(93.3 + 483202.02 * t) + 0.28 * sd(228.2 + 960400.89 * t) -
            0.28 * sd(318.3 + 6003.15 * t) - 0.17 * sd(217.6 - 407332.21 * t)
        return eclipticToEquatorial(norm360(lon), lat, obliquity(jd))
    }
}
