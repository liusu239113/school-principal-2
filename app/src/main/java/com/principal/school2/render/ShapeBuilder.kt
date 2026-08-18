package com.principal.school2.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 顶点格式:位置(3) + 法线(3) + 颜色(3) = 9 floats
 */
object ShapeBuilder {

    /** 立方体,中心在原点 */
    fun cube(w: Float, h: Float, d: Float): FloatArray {
        val x = w / 2f; val y = h / 2f; val z = d / 2f
        // 6 个面 * 2 三角形 * 3 顶点 = 36 顶点
        val verts = FloatArray(36 * 6)
        var i = 0
        fun emit(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
            verts[i++] = px; verts[i++] = py; verts[i++] = pz
            verts[i++] = nx; verts[i++] = ny; verts[i++] = nz
        }
        // 面(+X):顶/底/前/后/左/右,法线朝外
        // +Y 顶
        emit(-x, y, z, 0f, 1f, 0f); emit(x, y, z, 0f, 1f, 0f)
        emit(x, y, -z, 0f, 1f, 0f); emit(-x, y, z, 0f, 1f, 0f)
        emit(x, y, -z, 0f, 1f, 0f); emit(-x, y, -z, 0f, 1f, 0f)
        // -Y 底
        emit(-x, -y, z, 0f, -1f, 0f); emit(x, -y, -z, 0f, -1f, 0f)
        emit(x, -y, z, 0f, -1f, 0f); emit(-x, -y, z, 0f, -1f, 0f)
        emit(-x, -y, -z, 0f, -1f, 0f); emit(x, -y, -z, 0f, -1f, 0f)
        // +Z 前
        emit(-x, -y, z, 0f, 0f, 1f); emit(x, y, z, 0f, 0f, 1f)
        emit(x, -y, z, 0f, 0f, 1f); emit(-x, -y, z, 0f, 0f, 1f)
        emit(-x, y, z, 0f, 0f, 1f); emit(x, y, z, 0f, 0f, 1f)
        // -Z 后
        emit(-x, y, -z, 0f, 0f, -1f); emit(x, -y, -z, 0f, 0f, -1f)
        emit(x, y, -z, 0f, 0f, -1f); emit(-x, y, -z, 0f, 0f, -1f)
        emit(-x, -y, -z, 0f, 0f, -1f); emit(x, -y, -z, 0f, 0f, -1f)
        // -X 左
        emit(-x, -y, z, -1f, 0f, 0f); emit(-x, y, -z, -1f, 0f, 0f)
        emit(-x, y, z, -1f, 0f, 0f); emit(-x, -y, z, -1f, 0f, 0f)
        emit(-x, -y, -z, -1f, 0f, 0f); emit(-x, y, -z, -1f, 0f, 0f)
        // +X 右
        emit(x, -y, z, 1f, 0f, 0f); emit(x, y, z, 1f, 0f, 0f)
        emit(x, y, -z, 1f, 0f, 0f); emit(x, -y, z, 1f, 0f, 0f)
        emit(x, y, -z, 1f, 0f, 0f); emit(x, -y, -z, 1f, 0f, 0f)
        return verts
    }

    /** 圆柱体,中心在原点,高 h,半径 r */
    fun cylinder(r: Float, h: Float, segments: Int = 16): FloatArray {
        val half = h / 2f
        // 每段:侧面 6 顶点 + 顶 3 + 底 3 = 12 顶点
        val verts = FloatArray(segments * 12 * 6)
        var i = 0
        fun emit(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
            verts[i++] = px; verts[i++] = py; verts[i++] = pz
            verts[i++] = nx; verts[i++] = ny; verts[i++] = nz
        }
        for (s in 0 until segments) {
            val a0 = s * 2.0 * Math.PI / segments
            val a1 = (s + 1) * 2.0 * Math.PI / segments
            val cx0 = Math.cos(a0).toFloat() * r; val cz0 = Math.sin(a0).toFloat() * r
            val cx1 = Math.cos(a1).toFloat() * r; val cz1 = Math.sin(a1).toFloat() * r
            // 侧面(两个三角形)
            val nx0 = Math.cos(a0).toFloat(); val nz0 = Math.sin(a0).toFloat()
            val nx1 = Math.cos(a1).toFloat(); val nz1 = Math.sin(a1).toFloat()
            emit(cx0, -half, cz0, nx0, 0f, nz0)
            emit(cx1, half, cz1, nx1, 0f, nz1)
            emit(cx1, -half, cz1, nx1, 0f, nz1)
            emit(cx0, -half, cz0, nx0, 0f, nz0)
            emit(cx0, half, cz0, nx0, 0f, nz0)
            emit(cx1, half, cz1, nx1, 0f, nz1)
            // 顶面
            emit(0f, half, 0f, 0f, 1f, 0f)
            emit(cx0, half, cz0, 0f, 1f, 0f)
            emit(cx1, half, cz1, 0f, 1f, 0f)
            // 底面
            emit(0f, -half, 0f, 0f, -1f, 0f)
            emit(cx1, -half, cz1, 0f, -1f, 0f)
            emit(cx0, -half, cz0, 0f, -1f, 0f)
        }
        return verts
    }

    /** 圆锥体,中心在原点(顶点朝上),半径 r,高 h */
    fun cone(r: Float, h: Float, segments: Int = 16): FloatArray {
        val verts = FloatArray((segments * 3 + segments * 3) * 6)
        var i = 0
        fun emit(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
            verts[i++] = px; verts[i++] = py; verts[i++] = pz
            verts[i++] = nx; verts[i++] = ny; verts[i++] = nz
        }
        for (s in 0 until segments) {
            val a0 = s * 2.0 * Math.PI / segments
            val a1 = (s + 1) * 2.0 * Math.PI / segments
            val cx0 = Math.cos(a0).toFloat() * r; val cz0 = Math.sin(a0).toFloat() * r
            val cx1 = Math.cos(a1).toFloat() * r; val cz1 = Math.sin(a1).toFloat() * r
            // 侧面
            val nx0 = Math.cos(a0).toFloat(); val nz0 = Math.sin(a0).toFloat()
            val nx1 = Math.cos(a1).toFloat(); val nz1 = Math.sin(a1).toFloat()
            emit(0f, h / 2f, 0f, nx0 * 0.8f, 0.6f, nz0 * 0.8f)
            emit(cx0, -h / 2f, cz0, nx0, 0f, nz0)
            emit(cx1, -h / 2f, cz1, nx1, 0f, nz1)
            // 底面
            emit(0f, -h / 2f, 0f, 0f, -1f, 0f)
            emit(cx1, -h / 2f, cz1, 0f, -1f, 0f)
            emit(cx0, -h / 2f, cz0, 0f, -1f, 0f)
        }
        return verts
    }

    /** 球体(细分),半径 r */
    fun sphere(r: Float, stacks: Int = 8, slices: Int = 12): FloatArray {
        val verts = FloatArray(stacks * slices * 6 * 6)
        var i = 0
        fun emit(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
            verts[i++] = px; verts[i++] = py; verts[i++] = pz
            verts[i++] = nx; verts[i++] = ny; verts[i++] = nz
        }
        for (s in 0 until stacks) {
            val phi0 = s * Math.PI / stacks
            val phi1 = (s + 1) * Math.PI / stacks
            for (t in 0 until slices) {
                val th0 = t * 2.0 * Math.PI / slices
                val th1 = (t + 1) * 2.0 * Math.PI / slices
                fun p(phi: Double, th: Double): FloatArray {
                    val x = (Math.sin(phi) * Math.cos(th) * r).toFloat()
                    val y = (Math.cos(phi) * r).toFloat()
                    val z = (Math.sin(phi) * Math.sin(th) * r).toFloat()
                    val l = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    return floatArrayOf(x, y, z, x / l, y / l, z / l)
                }
                val v0 = p(phi0, th0); val v1 = p(phi1, th0); val v2 = p(phi0, th1); val v3 = p(phi1, th1)
                for (v in arrayOf(v0, v1, v2, v1, v3, v2)) {
                    verts[i++] = v[0]; verts[i++] = v[1]; verts[i++] = v[2]
                    verts[i++] = v[3]; verts[i++] = v[4]; verts[i++] = v[5]
                }
            }
        }
        return verts
    }
}
