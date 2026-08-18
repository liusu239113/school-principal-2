package com.principal.school2.render

import com.principal.school2.game.BuildingType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** 一栋建筑的全部网格数据(顶点:位置+法线+颜色) */
class CampusMesh(val floats: FloatArray) {
    val vertexCount: Int = floats.size / 9

    val buffer: FloatBuffer by lazy {
        val bb = ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(floats)
        fb.position(0)
        fb
    }
}

/**
 * 卡通建筑拼装器 - 用基础几何体(立方体/圆柱/圆锥/球)拼出卡通风格建筑
 * 每类建筑 = 主体 + 屋顶 + 装饰,配合明快的卡通配色
 */
object CampusMeshBuilder {

    /** 建筑最大高度(用于点击检测 AABB 和选中框) */
    fun heightFor(type: BuildingType): Float = when (type) {
        BuildingType.CLASSROOM -> 3.8f
        BuildingType.DORM -> 4.4f
        BuildingType.CANTEEN -> 3.6f
        BuildingType.LIBRARY -> 3.6f
        BuildingType.LAB -> 5.6f
        BuildingType.GYM -> 3.4f
        BuildingType.PARK -> 2.4f
        BuildingType.PLAYGROUND -> 0.6f
    }

    /** 构建建筑网格(以格中心为原点) */
    fun build(type: BuildingType): CampusMesh {
        val list = ArrayList<Float>()
        when (type) {
            BuildingType.CLASSROOM -> buildClassroom(list)
            BuildingType.DORM -> buildDorm(list)
            BuildingType.CANTEEN -> buildCanteen(list)
            BuildingType.LIBRARY -> buildLibrary(list)
            BuildingType.LAB -> buildLab(list)
            BuildingType.GYM -> buildGym(list)
            BuildingType.PARK -> buildPark(list)
            BuildingType.PLAYGROUND -> buildPlayground(list)
        }
        val arr = FloatArray(list.size)
        for (i in list.indices) arr[i] = list[i]
        return CampusMesh(arr)
    }

    private fun append(
        target: MutableList<Float>, shape: FloatArray,
        tx: Float, ty: Float, tz: Float,
        r: Float, g: Float, b: Float
    ) {
        var i = 0
        while (i < shape.size) {
            target.add(shape[i] + tx)
            target.add(shape[i + 1] + ty)
            target.add(shape[i + 2] + tz)
            target.add(shape[i + 3])
            target.add(shape[i + 4])
            target.add(shape[i + 5])
            target.add(r); target.add(g); target.add(b)
            i += 6
        }
    }

    private fun buildClassroom(l: MutableList<Float>) {
        // 淡黄色教学楼主体
        append(l, ShapeBuilder.cube(3.2f, 2.6f, 3.2f), 0f, 1.3f, 0f, 1.0f, 0.93f, 0.68f)
        // 红色锥形屋顶
        append(l, ShapeBuilder.cone(2.1f, 1.2f), 0f, 2.6f + 0.6f, 0f, 0.92f, 0.30f, 0.25f)
        // 蓝色窗户 x2
        append(l, ShapeBuilder.cube(0.55f, 0.75f, 0.08f), -0.85f, 1.6f, 1.61f, 0.45f, 0.68f, 0.92f)
        append(l, ShapeBuilder.cube(0.55f, 0.75f, 0.08f), 0.85f, 1.6f, 1.61f, 0.45f, 0.68f, 0.92f)
        // 棕色大门
        append(l, ShapeBuilder.cube(0.75f, 0.95f, 0.08f), 0f, 0.5f, 1.61f, 0.55f, 0.36f, 0.22f)
    }

    private fun buildDorm(l: MutableList<Float>) {
        // 浅蓝色宿舍主体
        append(l, ShapeBuilder.cube(3.2f, 3.8f, 3.2f), 0f, 1.9f, 0f, 0.62f, 0.76f, 0.92f)
        // 深蓝色平屋顶
        append(l, ShapeBuilder.cube(3.5f, 0.3f, 3.5f), 0f, 3.95f, 0f, 0.28f, 0.42f, 0.62f)
        // 白色阳台 x3
        append(l, ShapeBuilder.cube(0.55f, 0.85f, 0.12f), -1.0f, 1.2f, 1.61f, 0.96f, 0.96f, 0.96f)
        append(l, ShapeBuilder.cube(0.55f, 0.85f, 0.12f), 0f, 2.3f, 1.61f, 0.96f, 0.96f, 0.96f)
        append(l, ShapeBuilder.cube(0.55f, 0.85f, 0.12f), 1.0f, 3.4f, 1.61f, 0.96f, 0.96f, 0.96f)
        // 蓝色窗户(侧面)
        append(l, ShapeBuilder.cube(0.12f, 0.6f, 0.5f), 1.61f, 1.6f, 0.8f, 0.35f, 0.58f, 0.85f)
        append(l, ShapeBuilder.cube(0.12f, 0.6f, 0.5f), 1.61f, 1.6f, -0.8f, 0.35f, 0.58f, 0.85f)
    }

    private fun buildCanteen(l: MutableList<Float>) {
        // 橙色食堂主体
        append(l, ShapeBuilder.cube(3.4f, 2.2f, 3.4f), 0f, 1.1f, 0f, 1.0f, 0.66f, 0.30f)
        // 黄色圆屋顶
        append(l, ShapeBuilder.cone(2.3f, 1.3f), 0f, 2.2f + 0.65f, 0f, 1.0f, 0.85f, 0.35f)
        // 灰色烟囱
        append(l, ShapeBuilder.cylinder(0.26f, 1.1f), 0.9f, 3.3f, -0.9f, 0.62f, 0.62f, 0.62f)
        // 白色门帘
        append(l, ShapeBuilder.cube(1.3f, 0.8f, 0.1f), 0f, 0.4f, 1.71f, 0.95f, 0.9f, 0.8f)
    }

    private fun buildLibrary(l: MutableList<Float>) {
        // 浅绿色图书馆主体
        append(l, ShapeBuilder.cube(3.4f, 2.4f, 3.4f), 0f, 1.2f, 0f, 0.55f, 0.78f, 0.55f)
        // 深绿色平顶
        append(l, ShapeBuilder.cube(3.6f, 0.28f, 3.6f), 0f, 2.55f, 0f, 0.25f, 0.55f, 0.30f)
        // 中央圆塔(阅读角)
        append(l, ShapeBuilder.cylinder(0.5f, 1.3f), 0f, 3.3f, 0f, 0.35f, 0.62f, 0.35f)
        append(l, ShapeBuilder.sphere(0.62f), 0f, 4.2f, 0f, 0.22f, 0.5f, 0.28f)
        // 玻璃门
        append(l, ShapeBuilder.cube(0.9f, 1.1f, 0.08f), 0f, 0.55f, 1.71f, 0.7f, 0.85f, 0.95f)
    }

    private fun buildLab(l: MutableList<Float>) {
        // 淡紫色实验室主体
        append(l, ShapeBuilder.cube(3.2f, 2.4f, 3.2f), 0f, 1.2f, 0f, 0.78f, 0.62f, 0.88f)
        // 深紫色平顶
        append(l, ShapeBuilder.cube(3.5f, 0.26f, 3.5f), 0f, 2.55f, 0f, 0.45f, 0.3f, 0.58f)
        // 银色实验塔
        append(l, ShapeBuilder.cylinder(0.45f, 2.4f), -0.7f, 3.8f, -0.7f, 0.78f, 0.78f, 0.82f)
        // 红色天线
        append(l, ShapeBuilder.cylinder(0.07f, 0.9f), -0.7f, 5.45f, -0.7f, 0.95f, 0.3f, 0.3f)
        // 蓝色窗户
        append(l, ShapeBuilder.cube(0.5f, 0.6f, 0.08f), 0.8f, 1.5f, 1.61f, 0.5f, 0.72f, 0.92f)
    }

    private fun buildGym(l: MutableList<Float>) {
        // 红色体育馆主体
        append(l, ShapeBuilder.cube(3.8f, 2.6f, 3.4f), 0f, 1.3f, 0f, 0.88f, 0.32f, 0.32f)
        // 深红色屋顶
        append(l, ShapeBuilder.cube(4.1f, 0.5f, 3.7f), 0f, 2.85f, 0f, 0.62f, 0.2f, 0.2f)
        // 白色装饰条纹 x2
        append(l, ShapeBuilder.cube(0.5f, 1.8f, 0.08f), -1.3f, 1.3f, 1.71f, 0.96f, 0.96f, 0.96f)
        append(l, ShapeBuilder.cube(0.5f, 1.8f, 0.08f), 1.3f, 1.3f, 1.71f, 0.96f, 0.96f, 0.96f)
        // 入口雨棚
        append(l, ShapeBuilder.cube(1.6f, 0.15f, 0.8f), 0f, 1.7f, 1.8f, 0.5f, 0.5f, 0.5f)
    }

    private fun buildPark(l: MutableList<Float>) {
        // 草绿色地皮
        append(l, ShapeBuilder.cube(3.4f, 0.18f, 3.4f), 0f, 0.09f, 0f, 0.45f, 0.75f, 0.35f)
        // 大树:树干 + 树冠
        append(l, ShapeBuilder.cylinder(0.26f, 1.2f), -0.9f, 0.6f, -0.9f, 0.58f, 0.4f, 0.25f)
        append(l, ShapeBuilder.sphere(0.85f), -0.9f, 1.8f, -0.9f, 0.22f, 0.55f, 0.25f)
        // 小树
        append(l, ShapeBuilder.cylinder(0.2f, 0.8f), 1.1f, 0.4f, 0.9f, 0.58f, 0.4f, 0.25f)
        append(l, ShapeBuilder.sphere(0.6f), 1.1f, 1.3f, 0.9f, 0.3f, 0.62f, 0.3f)
        // 彩色花球
        append(l, ShapeBuilder.sphere(0.16f), 0.4f, 0.34f, -1.2f, 0.95f, 0.4f, 0.6f)
        append(l, ShapeBuilder.sphere(0.13f), -0.4f, 0.31f, 1.2f, 0.95f, 0.85f, 0.3f)
        append(l, ShapeBuilder.sphere(0.13f), 0.9f, 0.31f, -0.2f, 0.4f, 0.6f, 0.95f)
    }

    private fun buildPlayground(l: MutableList<Float>) {
        // 运动草地
        append(l, ShapeBuilder.cube(3.6f, 0.18f, 3.6f), 0f, 0.09f, 0f, 0.42f, 0.72f, 0.34f)
        // 灰白色跑道边框
        append(l, ShapeBuilder.cube(3.4f, 0.14f, 0.5f), 0f, 0.16f, 1.3f, 0.85f, 0.82f, 0.78f)
        append(l, ShapeBuilder.cube(3.4f, 0.14f, 0.5f), 0f, 0.16f, -1.3f, 0.85f, 0.82f, 0.78f)
        append(l, ShapeBuilder.cube(0.5f, 0.14f, 2.7f), 1.3f, 0.16f, 0f, 0.85f, 0.82f, 0.78f)
        append(l, ShapeBuilder.cube(0.5f, 0.14f, 2.7f), -1.3f, 0.16f, 0f, 0.85f, 0.82f, 0.78f)
        // 小足球门 x2
        append(l, ShapeBuilder.cube(0.7f, 0.7f, 0.1f), 0f, 0.45f, 1.6f, 0.95f, 0.98f, 0.95f)
        append(l, ShapeBuilder.cube(0.7f, 0.7f, 0.1f), 0f, 0.45f, -1.6f, 0.95f, 0.98f, 0.95f)
    }
}
