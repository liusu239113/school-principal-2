package com.principal.school2.game

import kotlin.random.Random

/**
 * 游戏引擎 - 校长模拟大学的核心经济/经营逻辑
 *
 * 核心循环:
 *  学生入学交学费 → 攒钱建造/升级设施 → 设施提升容量/满意度/声望
 *  → 声望吸引更多学生 → 更多学费
 */
class GameEngine {

    // ===== 基础状态 =====
    var money: Double = 3000.0          // 初始资金
    var students: Int = 20              // 初始学生
    var fame: Double = 10.0             // 声望 0-100
    var happy: Double = 60.0            // 满意度 0-100
    var day: Int = 1                    // 当前天数

    // ===== 网格 =====
    val GRID_SIZE = 5
    private val grid: Array<Array<Building?>> =
        Array(GRID_SIZE) { arrayOfNulls<Building?>(GRID_SIZE) }
    val buildings: MutableList<Building> = ArrayList()

    // ===== 事件通知 =====
    var eventListener: ((String) -> Unit)? = null

    val baseTuition = 60.0              // 每名学生每日学费
    val baseCapacity = 60               // 无建筑时基础容量
    val baseMaintenance = 40.0          // 每栋建筑每日维护费

    // ===== 初始化:给学校两栋初始建筑 =====
    init {
        val b1 = Building(BuildingType.CLASSROOM, 1, 2)
        val b2 = Building(BuildingType.CANTEEN, 3, 2)
        place(b1)
        place(b2)
    }

    private fun place(b: Building) {
        grid[b.gridX][b.gridZ] = b
        buildings.add(b)
    }

    fun getAt(x: Int, z: Int): Building? = grid[x][z]

    // ===== 派生状态 =====
    val studentCapacity: Int
        get() = baseCapacity + buildings.sumOf { it.capacityBonus }.toInt()

    val dailyIncome: Double
        get() = buildings.sumOf { it.dailyIncome }

    val tuitionIncome: Double
        get() = students * baseTuition

    val totalMaintenance: Double
        get() = buildings.size * baseMaintenance

    val freeGridCount: Int
        get() = GRID_SIZE * GRID_SIZE - buildings.size

    fun firstFreeCell(): Pair<Int, Int>? {
        for (x in 0 until GRID_SIZE) {
            for (z in 0 until GRID_SIZE) {
                if (grid[x][z] == null) return x to z
            }
        }
        return null
    }

    // ===== 建造 / 升级 =====
    fun canBuild(type: BuildingType): Boolean =
        money >= type.baseCost && freeGridCount > 0

    fun build(type: BuildingType): Boolean {
        if (!canBuild(type)) return false
        val cell = firstFreeCell() ?: return false
        val b = Building(type, cell.first, cell.second)
        b.builtDay = day
        money -= type.baseCost
        place(b)
        notifyEvent("🏗️ 建成了${type.emoji}${type.label}!花掉了 ¥${formatMoney(type.baseCost)}")
        return true
    }

    fun canUpgrade(b: Building): Boolean = money >= b.cost

    fun upgrade(b: Building): Boolean {
        if (!canUpgrade(b)) return false
        money -= b.cost
        b.level++
        notifyEvent("⬆️ ${b.type.emoji}${b.type.label} 升级到 Lv.${b.level}!")
        return true
    }

    /** 拆除建筑(返还部分资金) */
    fun demolish(b: Building): Boolean {
        grid[b.gridX][b.gridZ] = null
        buildings.remove(b)
        money += b.type.baseCost * 0.3
        notifyEvent("🧹 拆除了${b.type.emoji}${b.type.label},返还部分资金")
        return true
    }

    // ===== 每日结算 =====
    fun tickDay() {
        day++

        // 1. 收入 - 支出
        money += tuitionIncome + dailyIncome - totalMaintenance

        // 2. 学生动态
        val cap = studentCapacity
        val fameFactor = fame / 100.0
        val newStudents = Math.round(2.0 + fameFactor * 8.0).toInt()
        if (students < cap) {
            students = Math.min(cap, students + newStudents)
        }
        // 满意度太低 → 学生流失
        if (happy < 35.0 && students > 5) {
            val lost = Math.round((35.0 - happy) / 5.0).toInt()
            students = Math.max(5, students - lost)
        }

        // 3. 满意度变化:基础衰减 + 建筑加成 + 随机波动
        val buildingHappy = buildings.sumOf { it.happyBonus * 0.15 }
        val wave = Random.nextDouble(-1.5, 2.0)
        happy = clamp(happy - 1.2 + buildingHappy + wave, 0.0, 100.0)

        // 4. 声望缓慢增长
        val buildingFame = buildings.sumOf { it.fameBonus * 0.06 }
        fame = clamp(fame + buildingFame - 0.05, 0.0, 100.0)

        // 5. 随机事件
        if (Random.nextDouble() < 0.18) {
            triggerRandomEvent()
        }
    }

    // ===== 随机事件 =====
    private val goodEvents = arrayOf(
        Triple("🎉 政府教育拨款到账!", 600.0, 0.0),
        Triple("👏 杰出校友捐赠校园基金!", 1000.0, 3.0),
        Triple("🏆 我校竞赛获奖,声望大涨!", 0.0, 6.0),
        Triple("🌞 阳光明媚,学生心情很好!", 0.0, 0.0)
    )
    private val badEvents = arrayOf(
        Triple("🔧 教学设备老化,紧急维修!", -500.0, 0.0),
        Triple("😠 学生对食堂不满意,集体投诉!", 0.0, 0.0),
        Triple("🔌 宿舍电路故障,引发不满!", 0.0, 0.0),
        Triple("📉 部分学生转学离开!", 0.0, 0.0)
    )

    private fun triggerRandomEvent() {
        if (Random.nextDouble() < 0.5) {
            val e = goodEvents[Random.nextInt(goodEvents.size)]
            money = Math.max(0.0, money + e.second)
            if (e.third > 0) fame = clamp(fame + e.third, 0.0, 100.0)
            if (e.second > 0) notifyEvent("${e.first} +¥${formatMoney(e.second)}")
            else notifyEvent(e.first)
        } else {
            val e = badEvents[Random.nextInt(badEvents.size)]
            money = Math.max(0.0, money + e.second)
            when {
                e.first.contains("转学") -> {
                    val lost = Math.min(students, Math.max(2, students / 8))
                    students -= lost
                    notifyEvent("📉 有 $lost 名学生转学离开了...")
                }
                e.second < 0 -> notifyEvent("${e.first} -¥${formatMoney(-e.second)}")
                else -> {
                    happy = clamp(happy - 6.0, 0.0, 100.0)
                    notifyEvent(e.first)
                }
            }
        }
    }

    private fun notifyEvent(msg: String) {
        eventListener?.invoke(msg)
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double =
        Math.max(lo, Math.min(hi, v))

    companion object {
        fun formatMoney(v: Double): String {
            return when {
                v >= 10000 -> String.format("%.1f万", v / 10000.0)
                else -> String.format("%.0f", v)
            }
        }
    }
}
