package com.principal.school2.game

/**
 * 建筑实例 - 校园中的一栋建筑
 * @param type 建筑类型
 * @param gridX 网格 X(0-4)
 * @param gridZ 网格 Z(0-4)
 */
class Building(
    val type: BuildingType,
    var gridX: Int,
    var gridZ: Int
) {
    var level: Int = 1
    var builtDay: Int = 0

    val cost: Double get() = type.upgradeCost(level)
    val dailyIncome: Double get() = type.income(level)
    val capacityBonus: Double get() = type.capacity(level)
    val happyBonus: Double get() = type.happy(level)
    val fameBonus: Double get() = type.fame(level)

    /** 世界坐标中心位置(格间距 4 米) */
    fun worldX(): Float = gridX * 4f
    fun worldZ(): Float = gridZ * 4f
}
