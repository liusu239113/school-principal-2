package com.principal.school2.render

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 解析后的 OBJ 模型
 * vertexData 交错格式: pos(3) + normal(3) + uv(2),每顶点 8 floats
 */
class ObjModel(
    val vertexData: FloatArray,
    val indices: IntArray,
    val minY: Float,
    val height: Float
) {
    val triangleCount: Int = indices.size / 3
}

/**
 * 简易 OBJ 解析器(面向 mdl_to_obj.py 导出的格式:v / vt / f v/vt)
 * 法线按三角面计算(卡通低模 flat shading 效果)
 */
object ObjLoader {

    fun load(context: Context, assetPath: String): ObjModel {
        val positions = ArrayList<FloatArray>()
        val uvs = ArrayList<FloatArray>()
        val out = ArrayList<Float>()
        val idxOut = ArrayList<Int>()

        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        BufferedReader(InputStreamReader(context.assets.open(assetPath)))
            .forEachLine { line ->
                val t = line.trim()
                when {
                    t.startsWith("v ") -> {
                        val p = t.split(Regex("\\s+"))
                        val y = p[2].toFloat()
                        positions.add(floatArrayOf(p[1].toFloat(), y, p[3].toFloat()))
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                    t.startsWith("vt ") -> {
                        val p = t.split(Regex("\\s+"))
                        uvs.add(floatArrayOf(p[1].toFloat(), p[2].toFloat()))
                    }
                    t.startsWith("f ") -> {
                        val parts = t.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val a = parseCorner(parts[1])
                            val b = parseCorner(parts[2])
                            val c = parseCorner(parts[3])
                            emitTriangle(out, idxOut, a, b, c, positions, uvs)
                        }
                    }
                }
            }

        if (minY == Float.MAX_VALUE) minY = 0f
        if (maxY == Float.MIN_VALUE) maxY = 1f

        val data = FloatArray(out.size)
        for (i in out.indices) data[i] = out[i]
        val idx = IntArray(idxOut.size)
        for (i in idxOut.indices) idx[i] = idxOut[i]
        return ObjModel(data, idx, minY, maxY - minY)
    }

    private fun parseCorner(s: String): Pair<Int, Int> {
        val parts = s.split("/")
        val v = parts[0].toInt() - 1
        val vt = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() - 1 else -1
        return v to vt
    }

    private fun emitTriangle(
        out: MutableList<Float>,
        idxOut: MutableList<Int>,
        a: Pair<Int, Int>,
        b: Pair<Int, Int>,
        c: Pair<Int, Int>,
        positions: List<FloatArray>,
        uvs: List<FloatArray>
    ) {
        val p1 = positions[a.first]
        val p2 = positions[b.first]
        val p3 = positions[c.first]

        // 面法线
        var ux = p2[0] - p1[0]; var uy = p2[1] - p1[1]; var uz = p2[2] - p1[2]
        var vx = p3[0] - p1[0]; var vy = p3[1] - p1[1]; var vz = p3[2] - p1[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
        if (len > 1e-8f) {
            nx /= len; ny /= len; nz /= len
        } else {
            nx = 0f; ny = 1f; nz = 0f
        }

        val base = out.size / 8
        pushVertex(out, p1, floatArrayOf(nx, ny, nz), uvOf(uvs, a.second))
        pushVertex(out, p2, floatArrayOf(nx, ny, nz), uvOf(uvs, b.second))
        pushVertex(out, p3, floatArrayOf(nx, ny, nz), uvOf(uvs, c.second))
        idxOut.add(base); idxOut.add(base + 1); idxOut.add(base + 2)
    }

    private fun uvOf(uvs: List<FloatArray>, i: Int): FloatArray? =
        if (i >= 0 && i < uvs.size) uvs[i] else null

    private fun pushVertex(
        out: MutableList<Float>,
        p: FloatArray,
        n: FloatArray,
        uv: FloatArray?
    ) {
        out.add(p[0]); out.add(p[1]); out.add(p[2])
        out.add(n[0]); out.add(n[1]); out.add(n[2])
        out.add(uv?.get(0) ?: 0f)
        out.add(uv?.get(1) ?: 0f)
    }
}
