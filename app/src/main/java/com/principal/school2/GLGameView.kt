package com.principal.school2

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.principal.school2.game.Building
import com.principal.school2.render.CampusMeshBuilder
import com.principal.school2.render.GameRenderer

/**
 * 3D 校园视图
 * - 单指拖动:旋转相机
 * - 单击:射线拾取建筑
 */
class GLGameView(context: Context) : GLSurfaceView(context) {

    val renderer = GameRenderer()

    /** 建筑选中回调(点空地为 null) */
    var onBuildingSelected: ((Building?) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var movedDistance = 0f
    private var dragging = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                movedDistance = 0f
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                movedDistance += Math.abs(dx) + Math.abs(dy)
                if (movedDistance > 24f) {
                    dragging = true
                    // 拖动旋转相机
                    renderer.cameraYaw = renderer.cameraYaw - dx * 0.35f
                    downX = event.x
                    downY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && movedDistance <= 24f) {
                    pick(event.x, event.y)
                }
            }
        }
        return true
    }

    // ===== 射线拾取 =====
    private fun pick(sx: Float, sy: Float) {
        val bs = renderer.buildingsSnapshot
        val ray = renderer.screenRay(sx, sy, width, height) ?: return
        val ro = floatArrayOf(ray[0], ray[1], ray[2])
        val rd = floatArrayOf(ray[3], ray[4], ray[5])

        var best: Building? = null
        var bestT = Float.MAX_VALUE
        for (b in bs) {
            val h = CampusMeshBuilder.heightFor(b.type)
            val min = floatArrayOf(b.worldX() - 2.05f, 0f, b.worldZ() - 2.05f)
            val max = floatArrayOf(b.worldX() + 2.05f, h, b.worldZ() + 2.05f)
            val t = rayAABB(ro, rd, min, max)
            if (t > 0f && t < bestT) {
                bestT = t
                best = b
            }
        }
        onBuildingSelected?.invoke(best)
    }

    private fun rayAABB(ro: FloatArray, rd: FloatArray, min: FloatArray, max: FloatArray): Float {
        var tmin = -1e9f
        var tmax = 1e9f
        for (i in 0..2) {
            if (Math.abs(rd[i]) < 1e-8f) {
                if (ro[i] < min[i] || ro[i] > max[i]) return -1f
            } else {
                var t1 = (min[i] - ro[i]) / rd[i]
                var t2 = (max[i] - ro[i]) / rd[i]
                if (t1 > t2) {
                    val t = t1; t1 = t2; t2 = t
                }
                if (t1 > tmin) tmin = t1
                if (t2 < tmax) tmax = t2
                if (tmin > tmax) return -1f
            }
        }
        return if (tmax < 0f) -1f else tmin
    }
}
