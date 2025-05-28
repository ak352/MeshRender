package com.example.meshrender

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.media.Image as MediaImage
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meshrender.ui.theme.MeshRenderTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : ComponentActivity() {
    private val teapotPosition = floatArrayOf(0f, 0f, -1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeshRenderTheme {
                val context = LocalContext.current
                val rgbBitmap = remember {mutableStateOf<Bitmap?>(null)}
                val onRGB: (Bitmap) -> Unit = {bitmap -> rgbBitmap.value = bitmap}
                val renderer = remember { ARRenderer(context, teapotPosition, onRGB) }

                rgbBitmap.value?.let {bitmap ->
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),

                        )
                }

                CameraPermissionsWrapper {
                    AndroidView(factory = {
                        TextureView(it).apply {
                            isOpaque = false
                            surfaceTextureListener = renderer
                        }
                    })
                }

                var isSliderVisible by remember{mutableStateOf(false)}
                Column {
                    if (isSliderVisible) {
                        MeshSlider(teapotPosition)
                    }
                    OutlinedButton(
                        onClick = { isSliderVisible = !isSliderVisible },
                    )
                    {
                        Text("Toggle Slider")
                    }
                }

            }
        }
    }
}

@Composable
fun MeshSlider(meshPosition: FloatArray)
{
    var xSlider by remember { mutableStateOf(0f) }
    var ySlider by remember { mutableStateOf(0f) }
    var zSlider by remember { mutableStateOf(0f) }
    Slider(
        value = xSlider,
        onValueChange = {
            meshPosition[0] = it
            xSlider = it
        },
        valueRange = -10f..10f
    )
    Text(text = "X: ${xSlider.toString()}")
    Slider(
        value = ySlider,
        onValueChange = {
            meshPosition[1] = it
            ySlider = it
        },
        valueRange = -5f..5f
    )
    Text(text = "Y: ${ySlider.toString()}")
    Slider(
        value = zSlider,
        onValueChange = {
            meshPosition[2] = it
            zSlider = it
        },
        valueRange = -10f..10f
    )
    Text(text = "Z: ${zSlider.toString()}")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionsWrapper(content: @Composable () -> Unit)
{
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit)
    {
        if (!cameraPermissionState.status.isGranted)
        {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    when
    {
        cameraPermissionState.status.isGranted -> {
            content()
        }
        else -> {
            Text("Camera permission is required to use this feature.")
        }
    }
}


fun toBitmap(image: MediaImage) : Bitmap
{
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val jpegBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

}

val colormapJet = IntArray(256) { i->
    val r = if (i < 128) 0 else (i-128) * 2
    val g = if (i < 128) i*2 else 255 - (i-128)*2
    val b = if (i > 128) 0 else (128-i) * 2
    Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

fun depthToBitmap(image: android.media.Image, depthVizRange: Int) : Bitmap
{
    val buffer = image.planes[0].buffer
    buffer.rewind()
    val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    val rowStride = image.planes[0].rowStride
    val rowBuffer = ByteArray(rowStride)

    for (y in 0 until image.height)
    {
        buffer.position(y * rowStride)
        buffer.get(rowBuffer, 0, rowStride)
        for (x in 0 until image.width)
        {
            val low = rowBuffer[2*x].toInt() and 0xFF
            val high = rowBuffer[2*x+1].toInt() and 0xFF
            val depthMm = (high shl 8) or low
            val depthClamped = depthMm.coerceIn(0, depthVizRange)
            val gray = (255* (depthClamped / depthVizRange.toFloat())).toInt()
            val color = colormapJet[gray] // GraphicsColor.rgb(gray, gray, gray)
            bitmap.setPixel(x, y, color)
        }
    }

    return bitmap
}



class ARRenderer(
    private val context: Context,
    private val teapotPosition: FloatArray,
    private val onRGB: (Bitmap) -> Unit
) : TextureView.SurfaceTextureListener {
    private lateinit var session: Session
    private lateinit var eglHelper: EGLContextHelper
    private lateinit var teapot: Mesh
    private var hasInitialized: Boolean = false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if(!hasInitialized) {
            hasInitialized = true
            eglHelper = EGLContextHelper()
            eglHelper.init(surface)
            startRenderLoop(width, height)
        }

    }

    private fun startRenderLoop(width: Int, height: Int)
    {
        CoroutineScope(Dispatchers.Default).launch {
            eglHelper.makeCurrent()

            //tell AR session which camera texture to use
            val cameraTexture = IntArray(1)
            GLES20.glGenTextures(1, cameraTexture, 0)
            val cameraTextureId = cameraTexture[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            session = Session(context)
            val config = Config(session)
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
            session.configure(config)


            session.setCameraTextureName(cameraTextureId)
//            session.setDisplayGeometry(0, 480, 480) //TODO: hardcoded
            session.setDisplayGeometry(0, width, height)
            session.resume()

            teapot = Mesh(
                context.assets.open("teapot.obj"),
                context.assets.open("vertex_shader.glsl"),
                context.assets.open("fragment_shader.glsl")
            )
            teapot.init(width, height)

            val rotation = Matrix()
            rotation.postRotate(90f)

//            delay(5000L)
            val useAnchor = true
            var anchor: Anchor? = null

            var depthMap: ByteBuffer? = null
            var depthMapSize: IntArray? = null

            while (true)
            {
                val frame = session.update()
                if (frame.camera.trackingState != TrackingState.TRACKING)
                {
                    Log.d("ARCore", "Not tracking yet")
                    delay(100L)
                    continue
                }
                if (useAnchor && anchor == null)
                {
                    anchor = session.createAnchor(Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f)))
                }
                if (useAnchor && anchor?.trackingState != TrackingState.TRACKING)
                {
                    Log.d("ARCore", "Not tracking anchor yet")
                    delay(100L)
                    continue
                }


                try {
                    frame.acquireCameraImage().use {rgbImage->
                        val bitmap = toBitmap(rgbImage)
                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
                        Log.d("ARRenderer", "rotatedBitmap width ${rotatedBitmap.width} height ${rotatedBitmap.height}") //480x640

                        onRGB(rotatedBitmap)
                    }

                    frame.acquireDepthImage16Bits().use {depthImage->

//                        val bitmap = depthToBitmap(depthImage, 10000)
//                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
//                        onRGB(rotatedBitmap)

                        if (depthMap == null){
                            depthMap = ByteBuffer.allocateDirect(depthImage.planes[0].buffer.capacity())
                            depthMapSize = intArrayOf(depthImage.width, depthImage.height)
                        }
                        depthMap?.rewind()
                        val srcBuffer = depthImage.planes[0].buffer
                        srcBuffer.rewind()
                        depthMap?.put(srcBuffer)
                        depthMap?.rewind()
                        depthImage.close()
                    }

                }
                catch(e: NotYetAvailableException)
                {
                    Log.e("CameraImage", "Image not yet available")
                }
                catch(e: Exception)
                {
                    Log.e("CameraImage", "Unexpected error: ${e.message}")
                }

                val pose = frame.camera.pose
                val viewMatrix = FloatArray(16)
                frame.camera.getViewMatrix(viewMatrix, 0)
                val projectionMatrix = FloatArray(16)
                frame.camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 10f)
                Log.d("ARRenderer", "projectionMatrix ${projectionMatrix.joinToString(", ")}")

                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                val modelMatrix = FloatArray(16)
                if (useAnchor) {
                    val anchorPose = anchor?.pose
                    anchorPose?.toMatrix(modelMatrix, 0)
                }

                teapot.draw(useAnchor, modelMatrix, projectionMatrix, viewMatrix, teapotPosition,
                    depthMap, depthMapSize)

                eglHelper.swapBuffers()
                delay(16L)
            }
        }

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        session.pause()
        eglHelper.release()
        return true
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture){}
}
