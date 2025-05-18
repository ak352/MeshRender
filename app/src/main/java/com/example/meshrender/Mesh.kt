package com.example.meshrender

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.times


fun compileShader(type: Int, shaderCode: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, shaderCode)
    GLES20.glCompileShader(shader)

    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
    if (compileStatus[0] == 0) {
        val error = GLES20.glGetShaderInfoLog(shader)
        Log.e("ShaderCompile", "Source: $shaderCode")
        Log.e("ShaderCompile", "Error: $error")
        GLES20.glDeleteShader(shader)
        throw RuntimeException("Shader compilation failed: $error")
    }
    return shader
}


fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
    val programId = GLES20.glCreateProgram()
    GLES20.glAttachShader(programId, vertexShaderId)
    GLES20.glAttachShader(programId, fragmentShaderId)
    GLES20.glLinkProgram(programId)

    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        val error = GLES20.glGetProgramInfoLog(programId)
        GLES20.glDeleteProgram(programId)
        throw RuntimeException("Program linking failed: $error")
    }
    return programId
}

data class MeshData(val vertices: FloatArray, val indices: ShortArray, val normals: FloatArray)

class Mesh(
    val meshReader: InputStream,
    val vertexShaderReader: InputStream,
    val fragmentShaderReader: InputStream
) {

    private var shaderProgram: Int = 0
    private var vertexBufferId: Int = 0
    private var indexBufferId: Int = 0
    private var normalsBufferId: Int = 0
    private var mesh: MeshData = MeshData(FloatArray(1), ShortArray(1), FloatArray(1))

    fun meshFromOBJ(): MeshData {
        val verts = mutableListOf<Float>()
        val inds = mutableListOf<Short>()
        val normals = mutableListOf<Float>()

        meshReader.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("v ") -> {
                        val parts = line.split(" ")
                        parts.drop(1).forEach { part ->
                            verts += part.toFloat()
                        }
                    }

                    line.startsWith("f ") -> {
                        val parts = line.split(" ")
                        parts.drop(1).forEach {
                            val idx = it.split("/")[0].toShort()
                            inds += (idx - 1).toShort()
                        }
                    }

                    line.startsWith("vn ") -> {
                        val parts = line.split(" ")
                        parts.drop(1).forEach {part->
                            normals += part.toFloat()
                        }
                    }
                }
            }
        }
        return MeshData(verts.toFloatArray(), inds.toShortArray(), normals.toFloatArray())
    }

    fun init() {
        //Compile and link shader programs
        assert(EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT)
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER,
            vertexShaderReader.bufferedReader().use { it.readText() })
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderReader.bufferedReader().use { it.readText() })
        shaderProgram = linkProgram(vertexShader, fragmentShader)


        //3. Prepare geometry
        mesh = meshFromOBJ()
        val vertices = mesh.vertices
        Log.e(
            "ReadOBJ", "vertices[0] ${vertices[0]} vertices[1] ${vertices[1]} " +
                    "vertices[2] ${vertices[2]} vertices[3] ${vertices[3]}"
        )
        assert(mesh.vertices.size > 1000)
        assert(mesh.indices.size > 2000)
        assert(mesh.vertices.size == mesh.normals.size)


        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(mesh.vertices)
                position(0)
            }
        val indexBuffer = ByteBuffer.allocateDirect(mesh.indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

            .apply {
                put(mesh.indices)
                position(0)
            }

        val normalsBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(mesh.normals)
                position(0)
            }


        val buffers = IntArray(3)
        GLES20.glGenBuffers(3, buffers, 0)
        vertexBufferId = buffers[0]
        indexBufferId = buffers[1]
        normalsBufferId = buffers[2]

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            mesh.vertices.size * 4,
            vertexBuffer,
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            mesh.indices.size * 2,
            indexBuffer,
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalsBufferId)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            mesh.normals.size*4,
            normalsBuffer,
            GLES20.GL_STATIC_DRAW
        )

    }
    fun draw(projectionMatrix: FloatArray, viewMatrix: FloatArray, teapotPosition: FloatArray) {

        //1. Prepare model,view and projection matrices
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 0.05f, 0.05f, 0.05f)
        Log.d("GLES", "teapot position ${teapotPosition[0]}, ${teapotPosition[1]}, ${teapotPosition[2]}")
        Matrix.translateM(
            modelMatrix,
            0,
            teapotPosition[0],
            teapotPosition[1],
            teapotPosition[2]
//            0.5f,
//            -0.5f,
//            -3.5f
        ) //todo- assumed location of teapot in world space anchor


        val modelViewMatrix = FloatArray(16)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)

        val tempMatrix = FloatArray(16)
        Matrix.invertM(tempMatrix, 0, modelViewMatrix, 0)
        Matrix.transposeM(tempMatrix, 0, tempMatrix, 0)

        val normalMatrix = FloatArray(9)
        for(i in 0..2)
        {
            for (j in 0..2)
            {
                normalMatrix[i*3 + j] = tempMatrix[i*4+j]
            }
        }

        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        val mvpMatrixLocation = GLES20.glGetUniformLocation(shaderProgram, "u_MVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixLocation, 1, false, mvpMatrix, 0)
        assert(mvpMatrixLocation != -1)

        GLES20.glUseProgram(shaderProgram)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glFrontFace(GLES20.GL_CCW)

        //Bind VBOs
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)


        //4. Set attributes and uniforms
        val positionAttrib = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(
            positionAttrib,
            3,
            GLES20.GL_FLOAT,
            false,
            3 * 4,
            0
        )


        val normalAttrib = GLES20.glGetAttribLocation(shaderProgram, "a_Normal")
        GLES20.glEnableVertexAttribArray(normalAttrib)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalsBufferId)
        GLES20.glVertexAttribPointer(normalAttrib, 3, GLES20.GL_FLOAT, false, 3*4, 0)

        val lightDirection = floatArrayOf(0f, 0f, 1f)
        val lightHandle = GLES20.glGetUniformLocation(shaderProgram, "u_LightDirection")
        GLES20.glUniform3fv(lightHandle, 1, lightDirection, 0)

        val normalMatrixLocation = GLES20.glGetUniformLocation(shaderProgram, "u_NormalMatrix")
        GLES20.glUniformMatrix3fv(normalMatrixLocation, 1, false, normalMatrix, 0)

        //5. Draw the object
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indices.size, GLES20.GL_UNSIGNED_SHORT, 0)

    }


}