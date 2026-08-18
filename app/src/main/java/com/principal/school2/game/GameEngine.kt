package com.principal.school2.game

import android.content.SharedPreferences
import kotlin.random.Random

/**
 * 教师等级
 */
enum class TeacherRank(
    val label: String,
    val baseSalary: Double,
    val teachBonus: Double,     // 教学质量(满意度/学费加成)
    val researchBonus: Double,  // 科研产出
    val unlockFame: Double      // 需要的声望
) {
    LECTURER("讲师", 300.0, 1.0, 0.4, 0.0),
    ASSOCIATE("副教授", 650.0, 2.0, 1.5, 20.0),
    PROFESSOR("教授", 1400.0, 4.0, 3.0, 40.0),
    ACADEMICIAN("院士", 3000.0, 9.0, 8.0, 65.0)
}

class Teacher(val name: String, val rank: TeacherRank) {
    val salary: Double get() = rank.baseSalary
    val teachBonus: Double get() = rank.teachBonus
    val researchBonus: Double get() = rank.researchBonus
}

/**
 * 课程类型
 */
enum class CourseType(
    val label: String,
    val cost: Double,
    val tuitionMult: Double,       // 学费加成
    val requireBuilding: BuildingType,
    val maxLevel: Int = 3
) {
    BASIC("基础课", 500.0, 0.10, BuildingType.CLASSROOM),
    MAJOR("专业课", 1500.0, 0.25, BuildingType.CLASSROOM),
    ELECTIVE("选修课", 900.0, 0.15, BuildingType.LIBRARY),
    RESEARCH_C("研究课程", 2200.0, 0.30, BuildingType.LAB)
}

class Course(val type: CourseType) {
    var level: Int = 1
    val effect: Double get() = type.tuitionMult * level
}

/**
 * 社团
 */
enum class ClubType(val label: String, val emoji: String, val cost: Double, val happyBonus: Double, val fameBonus: Double) {
    FOOTBALL("足球队", "⚽", 800.0, 1.5, 0.5),
    MUSIC("音乐社", "🎵", 600.0, 1.5, 0.3),
    ART("美术社", "🎨", 600.0, 1.2, 0.3),
    SCIENCE("科技社", "🔬", 1000.0, 0.8, 1.0),
    LITERATURE("文学社", "📖", 500.0, 1.0, 0.4)
}

/**
 * 任务
 */
class GameTask(
    val id: Int,
    val desc: String,
    val target: Double,
    val reward: Double,
    val rewardType: String,   // money / fame / happy
    val progress: () -> Double
) {
    var completed: Boolean = false
    val done: Boolean get() = completed || progress() >= target
}

/**
 * 成就
 */
class Achievement(
    val id: Int,
    val name: String,
    val desc: String,
    val reward: Double,
    val condition: () -> Boolean
) {
    var unlocked: Boolean = false
}

/**
 * 游戏引擎 - 校长模拟大学的核心经营逻辑
 *
 * 时间:1 秒 = 1 天,7 天 = 1 周,4 周 = 1 学期,2 学期 = 1 学年
 * 系统:经济/学生/声望/满意度/师资/课程/科研/社团/任务/成就/存档
 */
class GameEngine {

    // ===== 基础状态 =====
    var money: Double = 5000.0
    var students: Int = 20
    var fame: Double = 10.0
    var happy: Double = 60.0
    var day: Int = 1
    var research: Double = 0.0
    var termResult: String = ""

    // ===== 网格 =====
    val GRID_SIZE = 5
    private val grid: Array<Array<Building?>> =
        Array(GRID_SIZE) { arrayOfNulls<Building?>(GRID_SIZE) }
    val buildings: MutableList<Building> = ArrayList()

    // ===== 师资/课程/社团 =====
    val teachers: MutableList<Teacher> = ArrayList()
    val courses: MutableList<Course> = ArrayList()
    val clubs: MutableList<ClubType> = ArrayList()

    // ===== 任务/成就 =====
    val tasks: MutableList<GameTask> = ArrayList()
    val achievements: MutableList<Achievement> = ArrayList()
    var tasksVersion = 0   // 用于 UI 刷新

    // ===== 事件 =====
    var eventListener: ((String) -> Unit)? = null
    var termListener: ((String) -> Unit)? = null

    // ===== 常量 =====
    val baseTuition = 60.0
    val baseCapacity = 60
    val baseMaintenance = 40.0
    val DAYS_PER_WEEK = 7
    val WEEKS_PER_TERM = 4

    // ===== 时间派生 =====
    val week: Int get() = (day - 1) / DAYS_PER_WEEK + 1
    val term: Int get() = (day - 1) / (DAYS_PER_WEEK * WEEKS_PER_TERM) + 1
    val year: Int get() = (term - 1) / 2 + 1
    val termDay: Int get() = (day - 1) % (DAYS_PER_WEEK * WEEKS_PER_TERM) + 1
    val isTermEnd: Boolean get() = termDay == DAYS_PER_WEEK * WEEKS_PER_TERM

    // ===== 初始化 =====
    init {
        val b1 = Building(BuildingType.CLASSROOM, 1, 2)
        val b2 = Building(BuildingType.CANTEEN, 3, 2)
        place(b1)
        place(b2)
        setupTasks()
        setupAchievements()
    }

    private fun place(b: Building) {
        grid[b.gridX][b.gridZ] = b
        buildings.add(b)
    }

    fun getAt(x: Int, z: Int): Building? = grid[x][z]

    // ===== 派生状态 =====
    val studentCapacity: Int
        get() = baseCapacity + buildings.sumOf { it.capacityBonus }.toInt()

    val buildingIncome: Double
        get() = buildings.sumOf { it.dailyIncome }

    val courseTuitionBonus: Double
        get() = courses.sumOf { it.effect }

    val tuitionIncome: Double
        get() = students * baseTuition * (1.0 + courseTuitionBonus)

    val teacherSalaryTotal: Double
        get() = teachers.sumOf { it.salary }

    val totalMaintenance: Double
        get() = buildings.size * baseMaintenance + teacherSalaryTotal

    val teacherHappyBonus: Double
        get() = teachers.sumOf { it.teachBonus } * 0.25

    val teacherResearch: Double
        get() = teachers.sumOf { it.researchBonus } * 0.15

    val clubHappyBonus: Double
        get() = clubs.sumOf { it.happyBonus } * 0.3

    val clubFameBonus: Double
        get() = clubs.sumOf { it.fameBonus } * 0.2

    val schoolRank: String
        get() {
            val score = fame + research * 0.01
            return when {
                score >= 90 -> "S 级名校"
                score >= 70 -> "A 级重点"
                score >= 50 -> "B 级优秀"
                score >= 30 -> "C 级普通"
                else -> "D 级起步"
            }
        }

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

    // ===== 建造 / 升级 / 拆除 =====
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

    fun demolish(b: Building): Boolean {
        grid[b.gridX][b.gridZ] = null
        buildings.remove(b)
        money += b.type.baseCost * 0.3
        notifyEvent("🧹 拆除了${b.type.emoji}${b.type.label},返还部分资金")
        return true
    }

    // ===== 师资 =====
    val teacherNames = arrayOf(
        "王老师", "李老师", "张老师", "陈老师", "刘老师", "赵老师", "周老师", "吴老师",
        "林教授", "徐老师", "孙老师", "何老师", "郭老师", "马老师"
    )
    private val hiredNames = HashSet<String>()

    fun canHireTeacher(rank: TeacherRank): Boolean =
        fame >= rank.unlockFame && money >= rank.baseSalary

    fun hireTeacher(rank: TeacherRank): Boolean {
        if (!canHireTeacher(rank)) return false
        var name = teacherNames[Random.nextInt(teacherNames.size)]
        var guard = 0
        while (hiredNames.contains(name) && guard < 20) {
            name = teacherNames[Random.nextInt(teacherNames.size)]
            guard++
        }
        hiredNames.add(name)
        money -= rank.baseSalary
        teachers.add(Teacher(name, rank))
        notifyEvent("🎓 聘请了${name}(${rank.label})!")
        return true
    }

    // ===== 课程 =====
    fun canOpenCourse(type: CourseType): Boolean {
        if (courses.any { it.type == type }) return true  // 升级
        if (type.requireBuilding == BuildingType.CLASSROOM &&
            !buildings.any { it.type == BuildingType.CLASSROOM }) return false
        if (type.requireBuilding == BuildingType.LIBRARY &&
            !buildings.any { it.type == BuildingType.LIBRARY }) return false
        if (type.requireBuilding == BuildingType.LAB &&
            !buildings.any { it.type == BuildingType.LAB }) return false
        return money >= type.cost
    }

    fun openCourse(type: CourseType): Boolean {
        val existing = courses.firstOrNull { it.type == type }
        if (existing != null) {
            if (existing.level >= type.maxLevel) return false
            val cost = type.cost * existing.level
            if (money < cost) return false
            money -= cost
            existing.level++
            notifyEvent("📘 升级了「${type.label}」到 Lv.${existing.level}!")
            return true
        }
        if (!canOpenCourse(type)) return false
        money -= type.cost
        courses.add(Course(type))
        notifyEvent("📖 开设了新课程「${type.label}」!")
        return true
    }

    // ===== 社团 =====
    fun canOpenClub(type: ClubType): Boolean =
        clubs.none { it == type } && money >= type.cost

    fun openClub(type: ClubType): Boolean {
        if (!canOpenClub(type)) return false
        money -= type.cost
        clubs.add(type)
        notifyEvent("🎉 成立了${type.emoji}${type.label}!")
        return true
    }

    // ===== 任务与成就 =====
    private fun setupTasks() {
        tasks.clear()
        tasks.add(GameTask(1, "建起 2 栋教学楼", 2.0, 800.0, "money",
            { buildings.count { it.type == BuildingType.CLASSROOM }.toDouble() }))
        tasks.add(GameTask(2, "建起 3 栋宿舍楼", 3.0, 1000.0, "money",
            { buildings.count { it.type == BuildingType.DORM }.toDouble() }))
        tasks.add(GameTask(3, "学生达到 200 人", 200.0, 1500.0, "money",
            { students.toDouble() }))
        tasks.add(GameTask(4, "声望达到 40", 40.0, 1200.0, "money",
            { fame }))
        tasks.add(GameTask(5, "聘请 2 位老师", 2.0, 800.0, "money",
            { teachers.size.toDouble() }))
        tasks.add(GameTask(6, "开设 2 门课程", 2.0, 1000.0, "money",
            { courses.size.toDouble() }))
        tasks.add(GameTask(7, "拥有 6 栋建筑", 6.0, 1500.0, "money",
            { buildings.size.toDouble() }))
        tasks.add(GameTask(8, "科研点达到 100", 100.0, 2000.0, "money",
            { research }))
        tasks.add(GameTask(9, "学生满意度达到 85", 85.0, 1800.0, "money",
            { happy }))
        tasks.add(GameTask(10, "成为 A 级名校", 70.0, 3000.0, "money",
            { fame }))
    }

    private fun setupAchievements() {
        achievements.clear()
        achievements.add(Achievement(1, "初出茅庐", "完成第一学期", 500.0,
            { term > 1 }))
        achievements.add(Achievement(2, "人气王", "学生达到 100 人", 800.0,
            { students >= 100 }))
        achievements.add(Achievement(3, "建筑大师", "拥有 8 栋建筑", 1000.0,
            { buildings.size >= 8 }))
        achievements.add(Achievement(4, "桃李满园", "聘请 4 位老师", 1200.0,
            { teachers.size >= 4 }))
        achievements.add(Achievement(5, "科研新星", "科研点达到 200", 1500.0,
            { research >= 200 }))
        achievements.add(Achievement(6, "名校之光", "声望达到 60", 2000.0,
            { fame >= 60 }))
        achievements.add(Achievement(7, "亿万富翁", "资金达到 50000", 3000.0,
            { money >= 50000 }))
        achievements.add(Achievement(8, "满腹经纶", "开设全部 4 门课程", 2500.0,
            { courses.size >= 4 }))
    }

    fun checkTasksAndAchievements() {
        var changed = false
        for (t in tasks) {
            if (!t.completed && t.done) {
                t.completed = true
                applyReward(t.rewardType, t.reward)
                notifyEvent("✅ 完成任务:${t.desc} +¥${formatMoney(t.reward)}")
                changed = true
            }
        }
        for (a in achievements) {
            if (!a.unlocked && a.condition()) {
                a.unlocked = true
                money += a.reward
                notifyEvent("🏆 解锁成就「${a.name}」+¥${formatMoney(a.reward)}")
                changed = true
            }
        }
        if (changed) tasksVersion++
    }

    private fun applyReward(type: String, value: Double) {
        when (type) {
            "money" -> money += value
            "fame" -> fame = clamp(fame + value, 0.0, 100.0)
            "happy" -> happy = clamp(happy + value, 0.0, 100.0)
        }
    }

    // ===== 每日结算 =====
    fun tickDay() {
        day++

        // 1. 收入 - 支出
        money += tuitionIncome + buildingIncome - totalMaintenance

        // 2. 科研产出
        research += teacherResearch

        // 3. 学生动态
        val cap = studentCapacity
        val fameFactor = fame / 100.0
        val newStudents = Math.round(2.0 + fameFactor * 8.0).toInt()
        if (students < cap) {
            students = Math.min(cap, students + newStudents)
        }
        if (happy < 35.0 && students > 5) {
            val lost = Math.round((35.0 - happy) / 5.0).toInt()
            students = Math.max(5, students - lost)
        }

        // 4. 满意度
        val buildingHappy = buildings.sumOf { it.happyBonus * 0.15 }
        val wave = Random.nextDouble(-1.5, 2.0)
        happy = clamp(
            happy - 1.2 + buildingHappy + teacherHappyBonus + clubHappyBonus + wave,
            0.0, 100.0
        )

        // 5. 声望
        val buildingFame = buildings.sumOf { it.fameBonus * 0.06 } + clubFameBonus
        fame = clamp(fame + buildingFame - 0.05, 0.0, 100.0)

        // 6. 学期末结算
        if (isTermEnd) {
            settleTerm()
        }

        // 7. 随机事件
        if (Random.nextDouble() < 0.15) {
            triggerRandomEvent()
        }

        // 8. 任务/成就检查
        checkTasksAndAchievements()
    }

    private fun settleTerm() {
        val growth = students
        val bonus = 500.0 + growth * 5.0
        money += bonus
        fame = clamp(fame + 2.0, 0.0, 100.0)
        termResult = "🎓 第${term}学期结算:在校生 $growth 人,获得拨款 ¥${formatMoney(bonus)},声望 +2"
        termListener?.invoke(termResult)
    }

    // ===== 随机事件 =====
    private val goodEvents = arrayOf(
        Triple("🎉 政府教育拨款到账!", 800.0, 0.0),
        Triple("👏 杰出校友捐赠校园基金!", 1200.0, 3.0),
        Triple("🏆 我校竞赛获奖,声望大涨!", 0.0, 6.0),
        Triple("💡 校企合作项目落地,获得资金!", 900.0, 1.0)
    )
    private val badEvents = arrayOf(
        Triple("🔧 教学设备老化,紧急维修!", -600.0, 0.0),
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

    // ===== 存档 =====
    fun save(prefs: SharedPreferences) {
        val e = prefs.edit()
        e.putInt("day", day)
        e.putFloat("money", money.toFloat())
        e.putInt("students", students)
        e.putFloat("fame", fame.toFloat())
        e.putFloat("happy", happy.toFloat())
        e.putFloat("research", research.toFloat())
        e.putString("buildings", buildings.joinToString(";") { b ->
            "${b.type.name},${b.level},${b.gridX},${b.gridZ},${b.builtDay}"
        })
        e.putString("teachers", teachers.joinToString(";") { "${it.name},${it.rank.name}" })
        e.putString("courses", courses.joinToString(";") { "${it.type.name},${it.level}" })
        e.putString("clubs", clubs.joinToString(";") { it.name })
        e.putString("tasksDone", tasks.filter { it.completed }.joinToString(",") { it.id.toString() })
        e.putString("achDone", achievements.filter { it.unlocked }.joinToString(",") { it.id.toString() })
        e.apply()
    }

    fun load(prefs: SharedPreferences) {
        day = prefs.getInt("day", 1)
        money = prefs.getFloat("money", 5000f).toDouble()
        students = prefs.getInt("students", 20)
        fame = prefs.getFloat("fame", 10f).toDouble()
        happy = prefs.getFloat("happy", 60f).toDouble()
        research = prefs.getFloat("research", 0f).toDouble()

        grid.forEachIndexed { x, row -> row.forEachIndexed { z, _ -> grid[x][z] = null } }
        buildings.clear()
        prefs.getString("buildings", "")!!.split(";").forEach { s ->
            if (s.isNotBlank()) {
                val p = s.split(",")
                if (p.size >= 5) {
                    try {
                        val b = Building(
                            BuildingType.valueOf(p[0]),
                            p[2].toInt(), p[3].toInt()
                        )
                        b.level = p[1].toInt()
                        b.builtDay = p[4].toInt()
                        place(b)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        teachers.clear()
        hiredNames.clear()
        prefs.getString("teachers", "")!!.split(";").forEach { s ->
            if (s.isNotBlank()) {
                val p = s.split(",")
                if (p.size >= 2) {
                    try {
                        teachers.add(Teacher(p[0], TeacherRank.valueOf(p[1])))
                        hiredNames.add(p[0])
                    } catch (_: Exception) {
                    }
                }
            }
        }

        courses.clear()
        prefs.getString("courses", "")!!.split(";").forEach { s ->
            if (s.isNotBlank()) {
                val p = s.split(",")
                if (p.size >= 2) {
                    try {
                        val c = Course(CourseType.valueOf(p[0]))
                        c.level = p[1].toInt()
                        courses.add(c)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        clubs.clear()
        prefs.getString("clubs", "")!!.split(";").forEach { s ->
            if (s.isNotBlank()) {
                try {
                    clubs.add(ClubType.valueOf(s))
                } catch (_: Exception) {
                }
            }
        }

        val doneTasks = prefs.getString("tasksDone", "")!!.split(",")
            .mapNotNull { it.toIntOrNull() }.toSet()
        for (t in tasks) t.completed = t.id in doneTasks

        val doneAch = prefs.getString("achDone", "")!!.split(",")
            .mapNotNull { it.toIntOrNull() }.toSet()
        for (a in achievements) a.unlocked = a.id in doneAch
    }

    companion object {
        fun formatMoney(v: Double): String {
            return when {
                v >= 10000 -> String.format("%.1f万", v / 10000.0)
                else -> String.format("%.0f", v)
            }
        }
    }
}
