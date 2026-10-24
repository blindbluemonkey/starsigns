package uk.owen.starsigns

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.*
import android.hardware.camera2.CameraCharacteristics
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener, LocationListener {

    private lateinit var preview: PreviewView
    private lateinit var sky: SkyView
    private lateinit var sensorManager: SensorManager
    private var locationManager: LocationManager? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Smoothed orientation quaternion (w, x, y, z)
    private val q = FloatArray(4)
    private var haveQ = false
    private val rawQ = FloatArray(4)
    private val rotMat = FloatArray(9)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        sky = SkyView(this)
        root.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(sky, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(buildButtons(), FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
    }

    private fun buildButtons(): View {
        val d = resources.displayMetrics.density
        fun button(label: String, onClick: (TextView) -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(0x99101830.toInt())
                setStroke((1 * d).toInt(), 0x66E8C46A)
            }
            setOnClickListener { onClick(this) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (24 * d).toInt())
            val lp = { LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = (6 * d).toInt(); marginEnd = (6 * d).toInt() } }
            addView(button("Camera: on") { b ->
                sky.cameraOn = !sky.cameraOn
                preview.visibility = if (sky.cameraOn) View.VISIBLE else View.INVISIBLE
                b.text = if (sky.cameraOn) "Camera: on" else "Camera: off"
                sky.invalidate()
            }, lp())
            addView(button("Night mode") { b ->
                sky.nightMode = !sky.nightMode
                b.setTextColor(if (sky.nightMode) Color.RED else Color.WHITE)
                sky.invalidate()
            }, lp())
            addView(button("Re-align") { sky.resetAlignment() }, lp())
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionsResult() {
        if (granted(Manifest.permission.CAMERA)) startCamera() else {
            sky.cameraOn = false
            preview.visibility = View.INVISIBLE
        }
        startLocation()
        sky.invalidate()
    }

    // ---------------- Camera ----------------

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase)
                // Work out the camera's field of view so the drawing matches the picture.
                val info = Camera2CameraInfo.from(camera.cameraInfo)
                val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (focal != null && size != null && focal > 0f) {
                    val long = max(size.width, size.height).toDouble()
                    val short = min(size.width, size.height).toDouble()
                    // CameraX preview defaults to 4:3, which matches most sensors' full area.
                    val previewLong = min(long, short * 4.0 / 3.0)
                    sky.cameraFovLong = Math.toDegrees(2 * atan(previewLong / (2 * focal)))
                    sky.cameraAspect = 4.0 / 3.0
                }
            } catch (e: Exception) {
                sky.cameraOn = false
                preview.visibility = View.INVISIBLE
            }
            sky.invalidate()
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------- Location ----------------

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val lm = locationManager ?: return
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { applyLocation(it, fix = true) }
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (p in providers) runCatching { lm.requestLocationUpdates(p, 60_000L, 500f, this, Looper.getMainLooper()) }
        }
    }

    private fun applyLocation(loc: Location, fix: Boolean) {
        sky.latitude = loc.latitude
        sky.longitude = loc.longitude
        sky.locationIsFix = fix
        sky.declination = GeomagneticField(
            loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis()
        ).declination
        sky.invalidate()
    }

    override fun onLocationChanged(location: Location) = applyLocation(location, fix = true)
    @Deprecated("Deprecated in API") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ---------------- Orientation sensors ----------------

    override fun onResume() {
        super.onResume()
        // Default declination for the fallback location until a fix arrives.
        if (!sky.locationIsFix) sky.declination = GeomagneticField(
            sky.latitude.toFloat(), sky.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
        val rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        rv?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        startLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager?.removeUpdates(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getQuaternionFromVector(rawQ, event.values)   // w, x, y, z
        if (!haveQ) { rawQ.copyInto(q); haveQ = true } else {
            // keep quaternions in the same hemisphere so the filter doesn't flip
            val dot = q[0] * rawQ[0] + q[1] * rawQ[1] + q[2] * rawQ[2] + q[3] * rawQ[3]
            val sign = if (dot < 0) -1f else 1f
            val a = 0.18f   // smoothing: lower = steadier, higher = snappier
            for (i in 0..3) q[i] += a * (sign * rawQ[i] - q[i])
            val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            for (i in 0..3) q[i] /= n
        }
        // getRotationMatrixFromVector wants (x, y, z, w)
        SensorManager.getRotationMatrixFromVector(rotMat, floatArrayOf(q[1], q[2], q[3], q[0]))
        sky.setRotationMatrix(rotMat)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        sky.compassUnreliable = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }
}
