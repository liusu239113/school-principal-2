package com.principal.school2.game

/**
 * 建筑类型定义 - 大学校园里的各类设施
 * 每类建筑有:基础成本、升级成本、各项效果系数
 */
enum class BuildingType(
    val label: String,
    val emoji: String,
    val baseCost: Double,
    val upCostFactor: Double,      // 每级升级成本 = baseCost * factor^(level)
    val dailyIncome: Double,       // 每日直接收入(学费之外的运营收入)
    val capacityBonus: Double,     // 学生容量加成(决定最大学生数)
    val happyBonus: Double,        // 满意度加成
    val fameBonus: Double          // 声望加成
) {
    CLASSROOM("教学楼", "🏫", 2000.0, 1.8, 80.0, 120.0, 1.0, 2.0),
    DORM("宿舍楼", "🏢", 1500.0, 1.7, 30.0, 150.0, 0.5, 0.5),
    CANTEEN("食堂", "🍚", 1200.0, 1.7, 20.0, 40.0, 3.0, 1.0),
    LIBRARY("图书馆", "📚", 2500.0, 1.9, 60.0, 30.0, 2.0, 4.0),
    LAB("实验室", "🔬", 3000.0, 1.9, 150.0, 20.0, 0.5, 3.0),
    GYM("体育馆", "🏀", 2000.0, 1.8, 40.0, 30.0, 2.5, 2.0),
    PARK("花园", "🌳", 800.0, 1.6, 0.0, 0.0, 2.0, 0.5),
    PLAYGROUND("操场", "⚽", 1000.0, 1.6, 10.0, 10.0, 1.5, 1.0);

    /** 计算第 level 级的升级成本 */
    fun upgradeCost(level: Int): Double =
        Math.round(baseCost * Math.pow(upCostFactor, (level - 1).toDouble()) * 100.0) / 100.0

    /** 当前等级每日效果 */
    fun income(level: Int) = dailyIncome * level
    fun capacity(level: Int) = capacityBonus * level
    fun happy(level: Int) = happyBonus * level
    fun fame(level: Int) = fameBonus * level

    companion object {
        fun gridIndex(type: BuildingType): Int = type.ordinal
    }
}
