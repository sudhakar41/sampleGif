package com.example.mygifapp.ui.main

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mygifapp.R
import pl.droidsonroids.gif.GifOptions
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10


class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // TODO: Use the ViewModel
    }

    private val VERTEX_SHADER_CODE = "attribute vec4 position;" +
            "uniform mediump mat4 texMatrix;" +
            "attribute vec4 coordinate;" +
            "varying vec2 textureCoordinate;" +
            "void main()" +
            "{" +
            "    gl_Position = position;" +
            "    mediump vec4 outCoordinate = texMatrix * coordinate;" +
            "    textureCoordinate = vec2(outCoordinate.s, 1.0 - outCoordinate.t);" +
            "}"

    private val FRAGMENT_SHADER_CODE = "varying mediump vec2 textureCoordinate;" +
            "uniform sampler2D texture;" +
            "uniform sampler2D texture1;" +
            "void main() { " +
            "    gl_FragColor = texture2D(texture, textureCoordinate) + texture2D(texture1, textureCoordinate);" +
            "}"

    lateinit var mGifTexImage2D: GifTexImage2D
    lateinit var mGifTexImage2D1: GifTexImage2D

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (!isOpenGLES2Supported()) {
            Toast.makeText(context, "gles2_not_supported", Toast.LENGTH_LONG).show()
            return null
        }
        mGifTexImage2D = try {
            val options = GifOptions()
            GifTexImage2D(
                    InputSource.ResourcesSource(resources, R.drawable.alphagif),
                    options
            )
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }

        mGifTexImage2D1 = try {
            val options = GifOptions()
            GifTexImage2D(
                    InputSource.ResourcesSource(resources, R.drawable.heart_effect),
                    options
            )
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        val view = inflater.inflate(R.layout.main_fragment, container, false)
        val surface = view.findViewById<GLSurfaceView>(R.id.glSurfaceView)
        surface.setEGLContextClientVersion(2)
        surface.setRenderer(Renderer())
        surface.holder.setFixedSize(mGifTexImage2D.width, mGifTexImage2D.height)
        mGifTexImage2D.startDecoderThread()
        mGifTexImage2D1.startDecoderThread()
        return view
    }

    override fun onDetach() {
        super.onDetach()
        mGifTexImage2D?.recycle()
        mGifTexImage2D1?.recycle()
    }

    inner class Renderer : GLSurfaceView.Renderer {
        private var textureLocation: Int = 0
        private var textureLocation1: Int = 0
        private var mTexMatrixLocation = 0
        private var texId1 = 0
        private var texId2 = 0
        val mTexMatrix = FloatArray(16)
        override fun onSurfaceCreated(p0: GL10?, p1: javax.microedition.khronos.egl.EGLConfig?) {
            val vertexShader: Int = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
            val pixelShader: Int = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
            val program = glCreateProgram()
            glAttachShader(program, vertexShader)
            glAttachShader(program, pixelShader)
            glLinkProgram(program)
            glDeleteShader(pixelShader)
            glDeleteShader(vertexShader)
            val positionLocation = glGetAttribLocation(program, "position")
            textureLocation = glGetUniformLocation(program, "texture")
            textureLocation1 = glGetUniformLocation(program, "texture1")
            mTexMatrixLocation = glGetUniformLocation(program, "texMatrix")
            val coordinateLocation = glGetAttribLocation(program, "coordinate")
            glUseProgram(program)
            val textureBuffer: Buffer = createFloatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
            val verticesBuffer: Buffer = createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            glVertexAttribPointer(coordinateLocation, 2, GL_FLOAT, false, 0, textureBuffer)
            glEnableVertexAttribArray(coordinateLocation)
            glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, verticesBuffer)
            glEnableVertexAttribArray(positionLocation)

            texId1 = createTex(mGifTexImage2D, 0)
            texId2 = createTex(mGifTexImage2D1, 1)

        }

        private fun createTex(gif: GifTexImage2D, level: Int): Int {
            val texNames = intArrayOf(0)
            glGenTextures(1, texNames, 0)
            glBindTexture(GL_TEXTURE_2D, texNames[0])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(
                    GL_TEXTURE_2D,
                    level,
                    GL_RGBA,
                    gif.getWidth(),
                    gif.getHeight(),
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    null
            )
            glBindTexture(GL_TEXTURE_2D, 0)
            return texNames[0]
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            val scaleX: Float = width.toFloat() / mGifTexImage2D.getWidth()
            val scaleY: Float = height.toFloat() / mGifTexImage2D.getHeight()
            Matrix.setIdentityM(mTexMatrix, 0)
            Matrix.scaleM(mTexMatrix, 0, scaleX, scaleY, 1f)
            Matrix.translateM(mTexMatrix, 0, 1 / scaleX / 2 - 0.5f, 1 / scaleY / 2 - 0.5f, 0f)
            glUniformMatrix4fv(mTexMatrixLocation, 1, false, mTexMatrix, 0)
        }

        override fun onDrawFrame(gl: GL10) {
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, texId1)
            mGifTexImage2D.glTexSubImage2D(GL_TEXTURE_2D,0)

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, texId2)
            mGifTexImage2D1.glTexSubImage2D(GL_TEXTURE_2D,1)

            glUniform1i(textureLocation, 0)

            glUniform1i(textureLocation1, 1)

            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }
    }


    fun loadShader(shaderType: Int, source: String): Int {
        val shader = glCreateShader(shaderType)
        glShaderSource(shader, source)
        glCompileShader(shader)
        return shader
    }

    private fun createFloatBuffer(floats: FloatArray): Buffer {
        return ByteBuffer
                .allocateDirect(floats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(floats)
                .rewind()
    }

    private fun isOpenGLES2Supported(): Boolean {
        val featureInfos = context!!.packageManager.systemAvailableFeatures
        if (featureInfos != null) {
            for (featureInfo in featureInfos) {
                if (featureInfo.name == null) {
                    return featureInfo.reqGlEsVersion and -0x10000 shr 16 >= 2
                }
            }
        }
        return false
    }

}