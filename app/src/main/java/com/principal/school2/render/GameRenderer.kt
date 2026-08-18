package com.principal.school2.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.principal.school2.game.Building
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 渲染器 - 3D 卡通校园
 * - 固定方向光 + 环境光(卡通明暗)
 * - 程序化建筑网格(VBO)
 * - 地面 + 网格线 + 选中高亮框
 */
class GameRenderer : android.opengl.GLSurfaceView.Renderer {

    // 渲染线程可见的建筑快照(由主线程更新)
    @Volatile
    var buildingsSnapshot: List<Building> = emptyList()

    @Volatile
    var selected: Pair<Int, Int>? = null

    var cameraYaw: Float = 0f
        set(value) {
            field = (value % 360f + 360f) % 360f
        }

    private var viewWidth = 1
    private var viewHeight = 1

    // ===== GPU 资源 =====
    private var program = 0
    private var uMvpLoc = 0
    private var uLightLoc = 0
    private var uAmbientLoc = 0
    private var uColorLoc = 0
    private var aPosLoc = 0
    private var aNormalLoc = 0
    private var aColorLoc = 0

    private val buildingVbos = ConcurrentHashMap<Building, Int>()
    private var groundVbo = 0
    private var gridVbo = 0
    private var wireVbo = 0

    private val modelM = FloatArray(16)
    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)
    private val mvpm = FloatArray(16)

    private val LIGHT_DIR = floatArrayOf(0.45f, 0.8f, 0.35f, 0f)

    // ===== Shader =====
    private val VERTEX_SHADER = """
        attribute vec4 aPos;
        attribute vec3 aNormal;
        attribute vec3 aColor;
        uniform mat4 uMVP;
        varying vec3 vNormal;
        varying vec3 vColor;
        void main() {
            gl_Position = uMVP * aPos;
            vNormal = aNormal;
            vColor = aColor;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec3 uLightDir;
        uniform vec3 uAmbient;
        uniform vec3 uColor;
        uniform int uUseColor;
        varying vec3 vNormal;
        varying vec3 vColor;
        void main() {
            vec3 n = normalize(vNormal);
            float diff = max(dot(n, normalize(uLightDir)), 0.0);
            vec3 col;
            if (uUseColor == 1) {
                col = uColor;
            } else {
                col = vColor;
            }
            vec3 lit = col * (uAmbient + (1.0 - uAmbient) * diff * 1.1);
            gl_FragColor = vec4(lit, 1.0);
        }
    """.trimIndent()

    // ===== GLSurfaceView.Renderer =====
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.62f, 0.82f, 0.94f, 1.0f)  // 浅蓝天空
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMVP")
        uLightLoc = GLES20.glGetUniformLocation(program, "uLightDir")
        uAmbientLoc = GLES20.glGetUniformLocation(program, "uAmbient")
        uColorLoc = GLES20.glGetUniformLocation(program, "uColor")
        val useColorLoc = GLES20.glGetUniformLocation(program, "uUseColor")
        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aNormalLoc = GLES20.glGetAttribLocation(program, "aNormal")
        aColorLoc = GLES20.glGetAttribLocation(program, "aColor")

        groundVbo = uploadShape(buildGround())
        gridVbo = uploadShape(buildGridLines())
        wireVbo = uploadShape(buildWireBox(2.0f, 2.0f, 2.0f))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // 清理已拆除建筑的 GL 缓冲(GL 线程安全)
        var v = pendingDelete.poll()
        while (v != null) {
            GLES20.glDeleteBuffers(1, intArrayOf(v), 0)
            v = pendingDelete.poll()
        }
        GLES20.glUniform3f(uLightLoc, LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2])
        GLES20.glUniform3f(uAmbientLoc, 0.52f, 0.52f, 0.52f)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseColor"), 0)

        // 相机:绕校园旋转的俯视角
        val aspect = if (viewHeight == 0) 1f else viewWidth.toFloat() / viewHeight
        computeCamera(viewM, projM, aspect)

        // 地面
        Matrix.setIdentityM(modelM, 0)
        Matrix.multiplyMM(mvpm, 0, projM, 0, viewM, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpm, 0)
        drawVbo(groundVbo, 6)

        // 网格线
        GLES20.glUniform3f(uColorLoc, 1f, 1f, 1f)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseColor"), 1)
        GLES20.glLineWidth(1.5f)
        drawVbo(gridVbo, 24, GLES20.GL_LINES)

        // 建筑
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseColor"), 0)
        val sel = selected
        for (b in buildingsSnapshot) {
            val vbo = buildingVbo(b)
            Matrix.setIdentityM(modelM, 0)
            Matrix.translateM(modelM, 0, b.worldX(), 0f, b.worldZ())
            Matrix.multiplyMM(mvpm, 0, projM, 0, viewM, 0)
            Matrix.multiplyMM(mvpm, 0, mvpm, 0, modelM, 0)
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpm, 0)
            drawVbo(vbo, GLES20.GL_TRIANGLES)
        }

        // 选中框
        if (sel != null) {
            val h = buildingsSnapshot.firstOrNull {
                it.gridX == sel.first && it.gridZ == sel.second
            }?.let { CampusMeshBuilder.heightFor(it.type) } ?: 3f
            Matrix.setIdentityM(modelM, 0)
            Matrix.translateM(modelM, 0, sel.first * 4f, h / 2f, sel.second * 4f)
            Matrix.scaleM(modelM, 0, 2.05f, h / 2f, 2.05f)
            Matrix.multiplyMM(mvpm, 0, projM, 0, viewM, 0)
            Matrix.multiplyMM(mvpm, 0, mvpm, 0, modelM, 0)
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpm, 0)
            GLES20.glUniform3f(uColorLoc, 0.3f, 1.0f, 0.3f)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseColor"), 1)
            GLES20.glLineWidth(3f)
            drawVbo(wireVbo, 24, GLES20.GL_LINES)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseColor"), 0)
        }
    }

    // ===== 相机矩阵 =====
    private fun computeCamera(view: FloatArray, proj: FloatArray, aspect: Float) {
        val cy = Math.sin(Math.toRadians(cameraYaw.toDouble())).toFloat()
        val cz = Math.cos(Math.toRadians(cameraYaw.toDouble())).toFloat()
        Matrix.setLookAtM(
            view, 0,
            cy * 26f, 19f, cz * 26f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.5f, 200f)
    }

    /**
     * 屏幕坐标 → 世界射线 [roX, roY, roZ, dirX, dirY, dirZ]
     * 与 onDrawFrame 使用相同的相机参数
     */
    fun screenRay(sx: Float, sy: Float, w: Int, h: Int): FloatArray? {
        if (w <= 0 || h <= 0) return null
        val view = FloatArray(16)
        val proj = FloatArray(16)
        computeCamera(view, proj, w.toFloat() / h)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, mvp, 0)) return null
        val nx = sx / w * 2f - 1f
        val ny = 1f - sy / h * 2f
        val near = floatArrayOf(nx, ny, -1f, 1f)
        val far = floatArrayOf(nx, ny, 1f, 1f)
        val nw = FloatArray(4)
        val fw = FloatArray(4)
        Matrix.multiplyMV(nw, 0, inv, 0, near, 0)
        Matrix.multiplyMV(fw, 0, inv, 0, far, 0)
        val ro = floatArrayOf(nw[0] / nw[3], nw[1] / nw[3], nw[2] / nw[3])
        val rf = floatArrayOf(fw[0] / fw[3], fw[1] / fw[3], fw[2] / fw[3])
        var dx = rf[0] - ro[0]
        var dy = rf[1] - ro[1]
        var dz = rf[2] - ro[2]
        val len = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        if (len < 1e-6f) return null
        dx /= len; dy /= len; dz /= len
        return floatArrayOf(ro[0], ro[1], ro[2], dx, dy, dz)
    }

    // ===== 建筑 VBO 管理 =====
    private val pendingDelete = java.util.concurrent.ConcurrentLinkedQueue<Int>()

    private fun buildingVbo(b: Building): Int {
        return buildingVbos.getOrPut(b) {
            uploadShape(CampusMeshBuilder.build(b.type))
        }
    }

    /** 主线程调用:同步建筑列表;GL 缓冲的删除延迟到 GL 线程 */
    fun setBuildings(buildings: List<Building>) {
        val current = buildingVbos.keys.toSet()
        val next = buildings.toSet()
        for (b in current) {
            if (b !in next) {
                buildingVbos.remove(b)?.let { pendingDelete.add(it) }
            }
        }
        buildingsSnapshot = buildings
    }

    // ===== 工具 =====
    private fun uploadShape(mesh: CampusMesh): Int {
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.floats.size * 4, mesh.buffer, GLES20.GL_STATIC_DRAW)
        return vbo[0]
    }

    private fun drawVbo(vbo: Int, vertexCount: Int, mode: Int = GLES20.GL_TRIANGLES) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 36, 0)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, 36, 12)
        GLES20.glEnableVertexAttribArray(aColorLoc)
        GLES20.glVertexAttribPointer(aColorLoc, 3, GLES20.GL_FLOAT, false, 36, 24)
        GLES20.glDrawArrays(mode, 0, vertexCount)
    }

    private fun createProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException("Link shader failed: " + GLES20.glGetProgramInfoLog(p))
        }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val status = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException("Compile shader failed: " + GLES20.glGetShaderInfoLog(sh))
        }
        return sh
    }

    /** 建筑选中框顶点(中心原点,半尺寸 1x1x1,GL_LINES) */
    private fun buildWireBox(hx: Float, hy: Float, hz: Float): CampusMesh {
        val f = FloatArray(24 * 9)
        var i = 0
        val pts = arrayOf(
            floatArrayOf(-hx, -hy, -hz), floatArrayOf(hx, -hy, -hz),
            floatArrayOf(hx, -hy, hz), floatArrayOf(-hx, -hy, hz),
            floatArrayOf(-hx, hy, -hz), floatArrayOf(hx, hy, -hz),
            floatArrayOf(hx, hy, hz), floatArrayOf(-hx, hy, hz)
        )
        val edges = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        for ((a, b) in edges) {
            for (p in arrayOf(pts[a], pts[b])) {
                f[i++] = p[0]; f[i++] = p[1]; f[i++] = p[2]
                f[i++] = 1f; f[i++] = 0f; f[i++] = 0f
                f[i++] = 1f; f[i++] = 1f; f[i++] = 1f
            }
        }
        return CampusMesh(f)
    }

    /** 地面(浅绿草地,40x40) */
    private fun buildGround(): CampusMesh {
        val f = FloatArray(12 * 9)
        var i = 0
        // 平铺矩形(2 三角形)
        fun vert(px: Float, pz: Float) {
            f[i++] = px; f[i++] = 0f; f[i++] = pz
            f[i++] = 0f; f[i++] = 1f; f[i++] = 0f
            f[i++] = 0.45f; f[i++] = 0.72f; f[i++] = 0.36f
        }
        vert(-20f, -20f); vert(20f, -20f); vert(20f, 20f)
        vert(-20f, -20f); vert(20f, 20f); vert(-20f, 20f)
        return CampusMesh(f)
    }

    /** 白色网格线(5x5 格 + 外框,GL_LINES:12 条线 = 24 顶点) */
    private fun buildGridLines(): CampusMesh {
        val f = FloatArray(24 * 9)
        var i = 0
        fun line(x0: Float, z0: Float, x1: Float, z1: Float) {
            for (p in arrayOf(floatArrayOf(x0, 0.02f, z0), floatArrayOf(x1, 0.02f, z1))) {
                f[i++] = p[0]; f[i++] = p[1]; f[i++] = p[2]
                f[i++] = 0f; f[i++] = 1f; f[i++] = 0f
                f[i++] = 1f; f[i++] = 1f; f[i++] = 1f
            }
        }
        for (g in 0..5) {
            val x = -2f + g * 4f
            line(x, -10f, x, 10f)
            line(-10f, x, 10f, x)
        }
        return CampusMesh(f)
    }
}
