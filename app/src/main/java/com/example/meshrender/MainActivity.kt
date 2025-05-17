package com.example.meshrender

import android.Manifest
import android.content.Context
import android.graphics.Camera
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meshrender.ui.theme.MeshRenderTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.ar.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeshRenderTheme {
                val context = LocalContext.current
                val renderer = remember { ARRenderer(context) }

                CameraPermissionsWrapper {
                    AndroidView(factory = {
                        TextureView(it).apply {
                            surfaceTextureListener = renderer
                        }
                    })
                }
            }
        }
    }
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


class ARRenderer(
    private val context: Context,
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
            startRenderLoop()
        }

    }

    private fun startRenderLoop()
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
            session.setCameraTextureName(cameraTextureId)
            session.resume()

            teapot = Mesh(
                context.assets.open("teapot.obj"),
                context.assets.open("vertex_shader.glsl"),
                context.assets.open("fragment_shader.glsl")
            )
            teapot.init()


            while (true)
            {
                val frame = session.update()
                val pose = frame.camera.pose
                val viewMatrix = FloatArray(16)
                frame.camera.getViewMatrix(viewMatrix, 0)
                val projectionMatrix = FloatArray(16)
                frame.camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 10f)

                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                teapot.draw(projectionMatrix, viewMatrix)

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
