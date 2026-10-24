package uk.owen.starsigns

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * Draws the sky over the camera preview. Every object is turned from RA/Dec into
 * altitude/azimuth for the current time and place, then into phone coordinates using
 * the rotation-vector sensor, then projected with a pinhole model matching the camera.
 */
class SkyView(context: Context) : View(context) {

    // ---- inputs set by the activity ----
    var latitude = 55.8
    var longitude = -3.7
    var locationIsFix = false
    var declination = 0f                       // magnetic declination, degrees east
    var compassUnreliable = false
    var cameraOn = true
    var nightMode = false
    /** Long-side field of view of the camera image in degrees and the long/short aspect ratio. */
    var cameraFovLong = 66.0
    var cameraAspect = 4.0 / 3.0

    private val rot = FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }
    private var haveRotation = false

    fun setRotationMatrix(m: FloatArray) {
        System.arraycopy(m, 0, rot, 0, 9)
        haveRotation = true
        postInvalidateOnAnimation()
    }

    // ---- user alignment ----
    private var azOffset = 0.0       // degrees
    private var altOffset = 0.0      // degrees
    private var zoom = 1.0
    fun resetAlignment() { azOffset = 0.0; altOffset = 0.0; zoom = 1.0; invalidate() }

    private val dp = resources.displayMetrics.density

    // ---- cached sky positions (recomputed once a second) ----
    private class StarPos(val star: Star, val h: Horizontal)
    private class ConPos(val con: Constellation, val stars: List<StarPos>, val centre: Horizontal)
    private class BodyPos(val body: Body, val h: Horizontal)

    private var cacheTime = 0L
    private var conPos: List<ConPos> = emptyList()
    private var bodyPos: List<BodyPos> = emptyList()
    private var ecliptic: List<Horizontal> = emptyList()

    private fun refreshPositions() {
        val now = System.currentTimeMillis()
        if (now - cacheTime < 1000 && conPos.isNotEmpty()) return
        cacheTime = now
        val jd = Astro.julianDay(now)
        val lst = Astro.localSiderealTime(jd, longitude)
        conPos = Catalog.constellations.map { c ->
            val sp = c.stars.map { st -> StarPos(st, Astro.toHorizontal(Astro.precess(st.ra, st.dec, jd), lst, latitude)) }
            ConPos(c, sp, centreOf(sp.map { it.h }))
        }
        bodyPos = Astro.bodies(jd).map { BodyPos(it, Astro.toHorizontal(it.pos, lst, latitude)) }
        ecliptic = Astro.eclipticPoints(jd).map { Astro.toHorizontal(it, lst, latitude) }
    }

    /** Spherical mean of a set of directions. */
    private fun centreOf(hs: List<Horizontal>): Horizontal {
        var x = 0.0; var y = 0.0; var z = 0.0
        for (h in hs) {
            val a = Math.toRadians(h.alt); val z0 = Math.toRadians(h.az)
            x += cos(a) * sin(z0); y += cos(a) * cos(z0); z += sin(a)
        }
        val r = sqrt(x * x + y * y + z * z)
        return Horizontal(Math.toDegrees(asin(z / r)), (Math.toDegrees(atan2(x, y)) + 360) % 360)
    }

    // ---- projection ----
    private var cx = 0f; private var cy = 0f; private var focal = 1000.0

    private fun updateFocal() {
        // The preview is scaled to fill the screen (centre-crop), so work out how tall the
        // camera image is on screen and derive a focal length in pixels from its FOV.
        val shownLong = max(height.toDouble(), width * cameraAspect)
        focal = (shownLong / 2.0) / tan(Math.toRadians(cameraFovLong / 2.0)) * zoom
        cx = width / 2f; cy = height / 2f
    }

    /** Phone-frame unit vector of a sky direction (camera looks along -z). */
    private fun deviceVector(h: Horizontal, out: DoubleArray) {
        val alt = Math.toRadians(h.alt + altOffset)
        val az = Math.toRadians(h.az - declination + azOffset)   // true -> magnetic frame
        val wx = cos(alt) * sin(az); val wy = cos(alt) * cos(az); val wz = sin(alt)
        out[0] = rot[0] * wx + rot[3] * wy + rot[6] * wz
        out[1] = rot[1] * wx + rot[4] * wy + rot[7] * wz
        out[2] = rot[2] * wx + rot[5] * wy + rot[8] * wz
    }

    private val tmp = DoubleArray(3)
    private val pt = PointF()

    /** Projects to [pt]; returns false if the point is behind the camera. */
    private fun project(h: Horizontal): Boolean {
        deviceVector(h, tmp)
        if (tmp[2] > -0.08) return false
        pt.x = (cx + focal * tmp[0] / -tmp[2]).toFloat()
        pt.y = (cy - focal * tmp[1] / -tmp[2]).toFloat()
        return true
    }

    private fun onScreen(x: Float, y: Float, margin: Float = 0f) =
        x > -margin && x < width + margin && y > -margin && y < height + margin

    // ---- paints ----
    private fun col(c: Int, alpha: Int = 255): Int {
        val base = if (nightMode) {
            val l = (0.3 * Color.red(c) + 0.59 * Color.green(c) + 0.11 * Color.blue(c)).toInt()
            Color.rgb(max(l, 60), 0, 0)
        } else c
        return Color.argb(alpha * Color.alpha(c) / 255, Color.red(base), Color.green(base), Color.blue(base))
    }

    private val gold = 0xFFE8C46A.toInt()
    private val pale = 0xFFB8C6FF.toInt()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
    private val dash = DashPathEffect(floatArrayOf(6 * dp, 8 * dp), 0f)
    private val glow = BlurMaskFilter(6 * dp, BlurMaskFilter.Blur.NORMAL)

    // ---- interaction ----
    private var tappedStar: Star? = null
    private var tappedAt = 0L
    private var lastProjected = ArrayList<Pair<Star, PointF>>()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoom = (zoom * d.scaleFactor).coerceIn(0.4, 4.0); invalidate(); return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress) return true
            // Drag the drawing so it sits on top of the real stars.
            azOffset -= Math.toDegrees(dx / focal)
            altOffset += Math.toDegrees(dy / focal)
            altOffset = altOffset.coerceIn(-30.0, 30.0)
            invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { resetAlignment(); return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val hit = lastProjected.minByOrNull { (_, p) -> hypot(p.x - e.x, p.y - e.y) }
            tappedStar = hit?.takeIf { hypot(it.second.x - e.x, it.second.y - e.y) < 40 * dp }?.first
            tappedAt = System.currentTimeMillis(); invalidate(); return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // ---- drawing ----
    override fun onDraw(canvas: Canvas) {
        refreshPositions()
        updateFocal()

        if (!cameraOn) {
            fillPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                col(0xFF050818.toInt()), col(0xFF101A3A.toInt()), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            fillPaint.shader = null
        }

        drawHorizon(canvas)
        drawEcliptic(canvas)

        val focus = focusedSign()
        lastProjected = ArrayList()
        for (cp in conPos) drawConstellation(canvas, cp, cp === focus)
        drawBodies(canvas)
        drawCrosshair(canvas)
        drawTappedStar(canvas)

        if (focus != null) drawInfoCard(canvas, focus) else drawPointer(canvas)
        drawHud(canvas)

        // Keep animating smoothly even without sensor events (time moves the sky).
        removeCallbacks(tick)
        postDelayed(tick, 1000)
    }

    private val tick = Runnable { invalidate() }

    private fun drawHorizon(canvas: Canvas) {
        linePaint.pathEffect = null
        linePaint.strokeWidth = 1.5f * dp
        linePaint.color = col(0xFF7FD6A6.toInt(), 170)
        drawCurve(canvas, (0..360 step 2).map { Horizontal(0.0, it.toDouble()) }, closed = false)

        textPaint.textSize = 15 * dp
        textPaint.textAlign = Paint.Align.CENTER
        val labels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        labels.forEachIndexed { i, l ->
            if (project(Horizontal(0.0, i * 45.0)) && onScreen(pt.x, pt.y)) {
                textPaint.color = col(0xFF7FD6A6.toInt(), if (l.length == 1) 255 else 180)
                textPaint.isFakeBoldText = l.length == 1
                canvas.drawText(l, pt.x, pt.y - 8 * dp, textPaint)
            }
        }
        textPaint.isFakeBoldText = false
    }

    private fun drawEcliptic(canvas: Canvas) {
        linePaint.strokeWidth = 1.2f * dp
        linePaint.color = col(gold, 90)
        linePaint.pathEffect = dash
        drawCurve(canvas, ecliptic, closed = true)
        linePaint.pathEffect = null
    }

    private val path = Path()
    private fun drawCurve(canvas: Canvas, pts: List<Horizontal>, closed: Boolean) {
        path.reset()
        var penDown = false
        val list = if (closed) pts + pts.first() else pts
        for (h in list) {
            if (project(h)) {
                if (penDown) path.lineTo(pt.x, pt.y) else path.moveTo(pt.x, pt.y)
                penDown = true
            } else penDown = false
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawConstellation(canvas: Canvas, cp: ConPos, focused: Boolean) {
        val c = cp.con
        val below = cp.centre.alt < 0
        val baseAlpha = when {
            focused -> 255
            c.zodiac -> 190
            else -> 110
        }.let { if (below) it / 3 else it }

        // project all stars first
        val pts = arrayOfNulls<PointF>(cp.stars.size)
        cp.stars.forEachIndexed { i, sp -> if (project(sp.h)) pts[i] = PointF(pt.x, pt.y) }

        // lines
        linePaint.strokeWidth = (if (focused) 2.6f else 1.4f) * dp
        val lc = if (c.zodiac) gold else pale
        if (focused) {
            linePaint.maskFilter = glow
            linePaint.color = col(lc, 120)
            for ((a, b) in c.lines) { val p = pts[a]; val q = pts[b]; if (p != null && q != null) canvas.drawLine(p.x, p.y, q.x, q.y, linePaint) }
            linePaint.maskFilter = null
        }
        linePaint.color = col(lc, baseAlpha * 3 / 4)
        for ((a, b) in c.lines) {
            val p = pts[a]; val q = pts[b]
            if (p != null && q != null && (onScreen(p.x, p.y, 400f) || onScreen(q.x, q.y, 400f)))
                canvas.drawLine(p.x, p.y, q.x, q.y, linePaint)
        }

        // stars
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 11 * dp
        cp.stars.forEachIndexed { i, sp ->
            val p = pts[i] ?: return@forEachIndexed
            if (!onScreen(p.x, p.y, 20f)) return@forEachIndexed
            val r = (max(1.6, (5.2 - sp.star.mag) * 1.35) * dp).toFloat()
            fillPaint.color = col(0xFFFFF6E0.toInt(), baseAlpha)
            canvas.drawCircle(p.x, p.y, r, fillPaint)
            lastProjected += sp.star to p
            if (focused || (sp.star.mag < 1.7 && !below)) {
                textPaint.color = col(0xFFFFFFFF.toInt(), if (focused) 220 else 150)
                canvas.drawText(sp.star.name, p.x + r + 4 * dp, p.y + 4 * dp, textPaint)
            }
        }

        // constellation label
        if (project(cp.centre) && onScreen(pt.x, pt.y, 50f)) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (if (focused) 22 else 16) * dp
            textPaint.isFakeBoldText = focused
            textPaint.color = col(if (c.zodiac) gold else pale, baseAlpha)
            val label = if (c.symbol.isNotEmpty()) "${c.symbol} ${c.name}" else c.name
            val offset = if (c.stars.size == 1) 22 * dp else 0f
            canvas.drawText(label, pt.x, pt.y + offset, textPaint)
            textPaint.isFakeBoldText = false
        }
    }

    private fun drawBodies(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12 * dp
        for (bp in bodyPos) {
            if (!project(bp.h) || !onScreen(pt.x, pt.y, 30f)) continue
            val below = bp.h.alt < 0
            val a = if (below) 90 else 255
            val r = bp.body.size * dp / 2
            fillPaint.color = col(bp.body.color, a)
            canvas.drawCircle(pt.x, pt.y, r, fillPaint)
            linePaint.strokeWidth = 1f * dp; linePaint.color = col(bp.body.color, a / 2)
            canvas.drawCircle(pt.x, pt.y, r + 4 * dp, linePaint)
            textPaint.color = col(bp.body.color, a)
            canvas.drawText(bp.body.name, pt.x + r + 7 * dp, pt.y - r, textPaint)
        }
    }

    private fun drawCrosshair(canvas: Canvas) {
        linePaint.strokeWidth = 1.5f * dp
        linePaint.color = col(0xFFFFFFFF.toInt(), 110)
        val r = 14 * dp
        canvas.drawCircle(cx, cy, r, linePaint)
    }

    private fun drawTappedStar(canvas: Canvas) {
        val s = tappedStar ?: return
        if (System.currentTimeMillis() - tappedAt > 4000) { tappedStar = null; return }
        val p = lastProjected.firstOrNull { it.first === s }?.second ?: return
        linePaint.strokeWidth = 2f * dp; linePaint.color = col(0xFFFFFFFF.toInt())
        canvas.drawCircle(p.x, p.y, 16 * dp, linePaint)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 14 * dp
        textPaint.color = col(0xFFFFFFFF.toInt())
        canvas.drawText("${s.name}  (mag ${"%.1f".format(s.mag)})", p.x, p.y - 24 * dp, textPaint)
    }

    /** The zodiac constellation nearest the middle of the screen, if one is reasonably close. */
    private fun focusedSign(): ConPos? {
        val limit = 0.42f * min(width, height)
        var best: ConPos? = null; var bestD = Float.MAX_VALUE
        for (cp in conPos) {
            if (!cp.con.zodiac) continue
            if (!project(cp.centre)) continue
            val d = hypot(pt.x - cx, pt.y - cy)
            if (d < bestD) { bestD = d; best = cp }
        }
        return if (bestD < limit) best else null
    }

    private fun compassName(az: Double): String {
        val names = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return names[(((az + 11.25) % 360) / 22.5).toInt()]
    }

    private val cardRect = RectF()
    private fun drawInfoCard(canvas: Canvas, cp: ConPos) {
        val c = cp.con
        val brightest = c.stars.minBy { it.mag }.name
        val alt = cp.centre.alt
        val where = if (alt >= 0) "%.0f° up, towards the %s".format(alt, compassName(cp.centre.az))
                    else "Below the horizon right now (%s)".format(compassName(cp.centre.az))
        val planets = bodyPos.filter { b ->
            cp.stars.any { angularDist(it.h, b.h) < 12.0 }
        }.joinToString(", ") { it.body.name }

        val lines = mutableListOf(
            "Sun sign: ${c.signDates}",
            "Brightest star: $brightest",
            where,
        )
        if (planets.isNotEmpty()) lines += "Also here tonight: $planets"

        val pad = 16 * dp
        val titleSize = 24 * dp; val bodySize = 14 * dp; val lineGap = 6 * dp
        val h = pad * 2 + titleSize + lineGap + lines.size * (bodySize + lineGap)
        val bottom = height - 88 * dp
        cardRect.set(16 * dp, bottom - h, width - 16 * dp, bottom)
        fillPaint.color = col(0xCC0B1030.toInt())
        canvas.drawRoundRect(cardRect, 18 * dp, 18 * dp, fillPaint)
        linePaint.strokeWidth = 1f * dp; linePaint.color = col(gold, 120)
        canvas.drawRoundRect(cardRect, 18 * dp, 18 * dp, linePaint)

        textPaint.textAlign = Paint.Align.LEFT
        var y = cardRect.top + pad + titleSize * 0.85f
        textPaint.textSize = titleSize; textPaint.color = col(gold); textPaint.isFakeBoldText = true
        canvas.drawText("${c.symbol}  ${c.name}", cardRect.left + pad, y, textPaint)
        textPaint.isFakeBoldText = false
        textPaint.textSize = 15 * dp; textPaint.color = col(0xFFCCCCCC.toInt())
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(c.meaning, cardRect.right - pad, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = bodySize; textPaint.color = col(0xFFEEEEEE.toInt())
        y += lineGap
        for (l in lines) { y += bodySize + lineGap; canvas.drawText(l, cardRect.left + pad, y, textPaint) }
    }

    private fun angularDist(a: Horizontal, b: Horizontal): Double {
        val a1 = Math.toRadians(a.alt); val a2 = Math.toRadians(b.alt)
        val dz = Math.toRadians(a.az - b.az)
        return Math.toDegrees(acos((sin(a1) * sin(a2) + cos(a1) * cos(a2) * cos(dz)).coerceIn(-1.0, 1.0)))
    }

    /** When no star sign is in view, point towards the nearest one that's above the horizon. */
    private fun drawPointer(canvas: Canvas) {
        var best: ConPos? = null; var bestAng = Double.MAX_VALUE
        val v = DoubleArray(3)
        for (cp in conPos) {
            if (!cp.con.zodiac || cp.centre.alt < 5) continue
            deviceVector(cp.centre, v)
            val ang = acos((-v[2]).coerceIn(-1.0, 1.0))
            if (ang < bestAng) { bestAng = ang; best = cp }
        }
        val target = best ?: return
        deviceVector(target.centre, v)
        var dx = v[0].toFloat(); var dy = -v[1].toFloat()
        val len = hypot(dx, dy); if (len < 1e-4) return
        dx /= len; dy /= len
        val r = 0.30f * min(width, height)
        val ax = cx + dx * r; val ay = cy + dy * r
        val ang = atan2(dy, dx)
        path.reset()
        val s = 16 * dp
        path.moveTo(ax + cos(ang) * s, ay + sin(ang) * s)
        path.lineTo(ax + cos(ang + 2.5f) * s, ay + sin(ang + 2.5f) * s)
        path.lineTo(ax + cos(ang - 2.5f) * s, ay + sin(ang - 2.5f) * s)
        path.close()
        fillPaint.color = col(gold, 230)
        canvas.drawPath(path, fillPaint)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 15 * dp
        textPaint.color = col(gold)
        canvas.drawText("${target.con.symbol} ${target.con.name} this way", ax - dx * 34 * dp, ay - dy * 34 * dp + 5 * dp, textPaint)
    }

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.UK)
    private fun drawHud(canvas: Canvas) {
        // which way is the camera pointing?
        val m = rot
        // camera axis (-z device) in world coords = -(third column of R)
        val ex = -m[2].toDouble(); val ny = -m[5].toDouble(); val up = -m[8].toDouble()
        val az = (Math.toDegrees(atan2(ex, ny)) + declination - azOffset + 720) % 360
        val alt = Math.toDegrees(asin(up.coerceIn(-1.0, 1.0))) - altOffset

        val ns = if (latitude >= 0) "N" else "S"; val ew = if (longitude >= 0) "E" else "W"
        val loc = "%.2f°%s %.2f°%s".format(abs(latitude), ns, abs(longitude), ew) + if (locationIsFix) "" else " (approx – waiting for GPS)"
        val l1 = "${timeFmt.format(Date())}  ·  $loc"
        val l2 = "Facing %s %.0f°  ·  %.0f° %s".format(compassName(az), az, abs(alt), if (alt >= 0) "up" else "down")

        val top = 40 * dp
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13 * dp
        textPaint.color = col(0xFFFFFFFF.toInt(), 220)
        textPaint.setShadowLayer(4 * dp, 0f, 0f, Color.BLACK)
        canvas.drawText(l1, 16 * dp, top, textPaint)
        canvas.drawText(l2, 16 * dp, top + 18 * dp, textPaint)
        var y = top + 36 * dp
        if (azOffset != 0.0 || altOffset != 0.0 || zoom != 1.0) {
            textPaint.color = col(gold, 220)
            canvas.drawText("Aligned by hand (double-tap to reset)", 16 * dp, y, textPaint); y += 18 * dp
        } else {
            textPaint.color = col(0xFFFFFFFF.toInt(), 150)
            canvas.drawText("Drag to line up with real stars · pinch to zoom", 16 * dp, y, textPaint); y += 18 * dp
        }
        if (compassUnreliable) {
            textPaint.color = col(0xFFFF8A65.toInt())
            canvas.drawText("Compass needs calibrating – wave the phone in a figure‑8", 16 * dp, y, textPaint)
        }
        if (!haveRotation) {
            textPaint.color = col(0xFFFF8A65.toInt())
            canvas.drawText("Waiting for motion sensors…", 16 * dp, y + 18 * dp, textPaint)
        }
        textPaint.clearShadowLayer()
    }
}
