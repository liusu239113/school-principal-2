package com.principal.school2

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.principal.school2.game.Achievement
import com.principal.school2.game.Building
import com.principal.school2.game.BuildingType
import com.principal.school2.game.ClubType
import com.principal.school2.game.CourseType
import com.principal.school2.game.GameEngine
import com.principal.school2.game.GameTask
import com.principal.school2.game.TeacherRank

/**
 * 校长我来当2 - 主界面
 * 竖屏 3D 校园模拟经营
 */
class MainActivity : Activity() {

    private lateinit var engine: GameEngine
    private lateinit var gameView: GLGameView
    private lateinit var sound: SoundManager
    private lateinit var prefs: SharedPreferences

    private lateinit var moneyText: TextView
    private lateinit var studentText: TextView
    private lateinit var fameText: TextView
    private lateinit var happyText: TextView
    private lateinit var dayText: TextView
    private lateinit var eventText: TextView
    private lateinit var infoPanel: View
    private lateinit var infoTitle: TextView
    private lateinit var infoDetail: TextView
    private lateinit var upgradeBtn: Button
    private lateinit var demolishBtn: Button
    private lateinit var buildBar: View
    private lateinit var buildPanel: View
    private lateinit var buildingList: LinearLayout
    private lateinit var contentPanel: View
    private lateinit var contentTitle: TextView
    private lateinit var contentList: LinearLayout

    private var selectedBuilding: Building? = null
    private var currentTab = 0  // 0=校园 1=任务 2=师资 3=学校

    private val handler = Handler(Looper.getMainLooper())
    private var eventHideRunnable: Runnable? = null

    private val dayRunnable = object : Runnable {
        override fun run() {
            engine.tickDay()
            refreshHud()
            handler.postDelayed(this, 1000)
        }
    }

    private val hudRunnable = object : Runnable {
        override fun run() {
            refreshHud()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("school2_save", MODE_PRIVATE)
        engine = GameEngine()
        engine.load(prefs)
        sound = SoundManager(this)

        gameView = findViewById(R.id.gameView)
        moneyText = findViewById(R.id.moneyText)
        studentText = findViewById(R.id.studentText)
        fameText = findViewById(R.id.fameText)
        happyText = findViewById(R.id.happyText)
        dayText = findViewById(R.id.dayText)
        eventText = findViewById(R.id.eventText)
        infoPanel = findViewById(R.id.infoPanel)
        infoTitle = findViewById(R.id.infoTitle)
        infoDetail = findViewById(R.id.infoDetail)
        upgradeBtn = findViewById(R.id.upgradeBtn)
        demolishBtn = findViewById(R.id.demolishBtn)
        buildBar = findViewById(R.id.buildBar)
        buildPanel = findViewById(R.id.buildPanel)
        buildingList = findViewById(R.id.buildingList)
        contentPanel = findViewById(R.id.contentPanel)
        contentTitle = findViewById(R.id.contentTitle)
        contentList = findViewById(R.id.contentList)

        engine.eventListener = { msg ->
            showEvent(msg)
            sound.play("event")
        }
        engine.termListener = { msg -> showEvent(msg, 4000) }

        gameView.onBuildingSelected = { b -> onPick(b) }

        // 按钮事件
        findViewById<Button>(R.id.buildBtn).setOnClickListener {
            sound.play("click")
            openBuildPanel()
        }
        findViewById<Button>(R.id.closeBuildBtn).setOnClickListener {
            sound.play("click")
            closeBuildPanel()
        }
        findViewById<Button>(R.id.closeInfoBtn).setOnClickListener {
            sound.play("click")
            clearSelection()
        }
        upgradeBtn.setOnClickListener {
            sound.play("click")
            val b = selectedBuilding ?: return@setOnClickListener
            if (engine.upgrade(b)) {
                sound.play("upgrade")
                refreshHud()
                showSelection(b)
            } else {
                showEvent("💸 资金不足,无法升级!")
            }
        }
        demolishBtn.setOnClickListener {
            sound.play("click")
            val b = selectedBuilding ?: return@setOnClickListener
            engine.demolish(b)
            clearSelection()
        }

        // 底部导航
        findViewById<Button>(R.id.tabCampus).setOnClickListener { switchTab(0) }
        findViewById<Button>(R.id.tabTasks).setOnClickListener { switchTab(1) }
        findViewById<Button>(R.id.tabStaff).setOnClickListener { switchTab(2) }
        findViewById<Button>(R.id.tabSchool).setOnClickListener { switchTab(3) }

        buildBuildingCards()

        switchTab(0)
        syncToRenderer()
        refreshHud()
        showEvent("🎓 欢迎新校长上任!先看看任务,建设你的大学!")

        handler.post(dayRunnable)
        handler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
        sound.startBgm()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
        engine.save(prefs)
        sound.stopBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.save(prefs)
        handler.removeCallbacksAndMessages(null)
        sound.release()
    }

    // ===== Tab 切换 =====
    private fun switchTab(tab: Int) {
        currentTab = tab
        clearSelection()
        closeBuildPanel()
        if (tab == 0) {
            contentPanel.visibility = View.GONE
            buildBar.visibility = View.VISIBLE
        } else {
            buildBar.visibility = View.GONE
            contentPanel.visibility = View.VISIBLE
            when (tab) {
                1 -> buildTasksPanel()
                2 -> buildStaffPanel()
                3 -> buildSchoolPanel()
            }
        }
        refreshTabHighlight()
    }

    private fun refreshTabHighlight() {
        // 简单反馈:当前 tab 用文字高亮
    }

    // ===== 3D 拾取 =====
    private fun onPick(b: Building?) {
        if (currentTab != 0) return
        if (b == null) {
            clearSelection()
            return
        }
        sound.play("click")
        selectedBuilding = b
        gameView.renderer.selected = b.gridX to b.gridZ
        showSelection(b)
    }

    private fun clearSelection() {
        selectedBuilding = null
        gameView.renderer.selected = null
        infoPanel.visibility = View.GONE
    }

    private fun showSelection(b: Building) {
        infoTitle.text = "${b.type.emoji} ${b.type.label}  Lv.${b.level}"
        infoDetail.text = buildString {
            appendLine("每日收入 +¥${GameEngine.formatMoney(b.dailyIncome)}")
            appendLine("学生容量 +${b.capacityBonus.toInt()}")
            appendLine("满意度 +${b.happyBonus} / 声望 +${b.fameBonus}")
            append("升级费用 ¥${GameEngine.formatMoney(b.cost)}")
        }
        upgradeBtn.isEnabled = engine.canUpgrade(b)
        infoPanel.visibility = View.VISIBLE
        buildBar.visibility = View.GONE
    }

    // ===== 建造面板 =====
    private fun buildBuildingCards() {
        buildingList.removeAllViews()
        for (type in BuildingType.values()) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.CENTER
            card.setPadding(16, 12, 16, 12)
            card.setBackgroundResource(R.drawable.card_bg)

            val name = TextView(this)
            name.text = "${type.emoji} ${type.label}"
            name.textSize = 14f
            name.setTextColor(resources.getColor(R.color.textLight))

            val price = TextView(this)
            price.text = "¥${GameEngine.formatMoney(type.baseCost)}"
            price.textSize = 12f
            price.setTextColor(resources.getColor(R.color.btnGold))
            price.setPadding(0, 2, 0, 6)

            val btn = Button(this)
            btn.text = "建造"
            btn.textSize = 14f
            btn.setTextColor(resources.getColor(R.color.textLight))
            btn.background = resources.getDrawable(R.drawable.btn_primary)
            btn.stateListAnimator = null
            btn.setOnClickListener {
                sound.play("click")
                if (engine.build(type)) {
                    sound.play("build")
                    closeBuildPanel()
                    refreshHud()
                    syncToRenderer()
                } else {
                    showEvent(
                        if (engine.freeGridCount <= 0) "🗺️ 校园已满!先拆除旧建筑吧"
                        else "💸 资金不足!"
                    )
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(8, 8, 8, 8)
            card.addView(name)
            card.addView(price)
            card.addView(btn)
            buildingList.addView(card, lp)
        }
    }

    private fun openBuildPanel() {
        clearSelection()
        buildBar.visibility = View.GONE
        refreshBuildButtons()
        buildPanel.visibility = View.VISIBLE
    }

    private fun closeBuildPanel() {
        buildPanel.visibility = View.GONE
        if (currentTab == 0) buildBar.visibility = View.VISIBLE
    }

    private fun refreshBuildButtons() {
        for (i in 0 until buildingList.childCount) {
            val card = buildingList.getChildAt(i) as LinearLayout
            val btn = card.getChildAt(card.childCount - 1) as Button
            val type = BuildingType.values()[i]
            btn.isEnabled = engine.canBuild(type)
        }
    }

    // ===== 任务面板 =====
    private fun buildTasksPanel() {
        contentTitle.text = "📋 校长任务"
        contentList.removeAllViews()
        var done = 0
        for (t in engine.tasks) {
            if (t.completed) done++
            contentList.addView(taskCard(t))
        }
        contentList.addView(sectionText("完成 $done / ${engine.tasks.size} 个任务"))
    }

    private fun taskCard(t: GameTask): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(14, 10, 14, 10)
        card.setBackgroundResource(R.drawable.card_bg)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 8)
        card.layoutParams = lp

        val title = TextView(this)
        title.text = if (t.completed) "✅ ${t.desc}" else "📌 ${t.desc}"
        title.textSize = 14f
        title.setTextColor(resources.getColor(R.color.textLight))
        title.setTypeface(null, Typeface.BOLD)

        val progress = Math.min(t.progress(), t.target)
        val info = TextView(this)
        info.text = if (t.completed) {
            "已完成 +¥${GameEngine.formatMoney(t.reward)}"
        } else {
            "进度 ${progress.toInt()}/${t.target.toInt()} · 奖励 ¥${GameEngine.formatMoney(t.reward)}"
        }
        info.textSize = 12f
        info.setTextColor(resources.getColor(R.color.textSoft))

        card.addView(title)
        card.addView(info)
        return card
    }

    // ===== 师资面板 =====
    private fun buildStaffPanel() {
        contentTitle.text = "🎓 师资队伍"
        contentList.removeAllViews()

        contentList.addView(sectionText("在校教师:${engine.teachers.size} 人 · 日薪合计 ¥${GameEngine.formatMoney(engine.teacherSalaryTotal)}"))
        for (t in engine.teachers) {
            val row = TextView(this)
            row.text = "👨‍🏫 ${t.name} · ${t.rank.label} · 教学+${t.teachBonus} 科研+${t.researchBonus}"
            row.textSize = 13f
            row.setTextColor(resources.getColor(R.color.textSoft))
            row.setPadding(14, 6, 14, 6)
            contentList.addView(row)
        }

        contentList.addView(sectionText("人才市场(声望需达标)"))
        for (rank in TeacherRank.values()) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(14, 10, 14, 10)
            card.setBackgroundResource(R.drawable.card_bg)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 8)
            card.layoutParams = lp

            val name = TextView(this)
            name.text = "${rank.label} · 日薪 ¥${GameEngine.formatMoney(rank.baseSalary)}"
            name.textSize = 14f
            name.setTextColor(resources.getColor(R.color.textLight))
            name.setTypeface(null, Typeface.BOLD)

            val desc = TextView(this)
            desc.text = "教学+${rank.teachBonus} 科研+${rank.researchBonus} 需声望${rank.unlockFame.toInt()}"
            desc.textSize = 12f
            desc.setTextColor(resources.getColor(R.color.textSoft))

            val btn = Button(this)
            btn.text = "聘请"
            btn.textSize = 14f
            btn.setTextColor(resources.getColor(R.color.textLight))
            btn.background = resources.getDrawable(R.drawable.btn_primary)
            btn.stateListAnimator = null
            btn.isEnabled = engine.canHireTeacher(rank)
            btn.setOnClickListener {
                sound.play("click")
                if (engine.hireTeacher(rank)) {
                    sound.play("achievement")
                    buildStaffPanel()
                    refreshHud()
                } else {
                    showEvent("💸 资金或声望不足!")
                }
            }

            card.addView(name)
            card.addView(desc)
            card.addView(btn)
            contentList.addView(card)
        }
    }

    // ===== 学校面板 =====
    private fun buildSchoolPanel() {
        contentTitle.text = "🏆 学校发展"
        contentList.removeAllViews()

        contentList.addView(sectionText("学校评级:${engine.schoolRank} · 科研点 ${engine.research.toInt()}"))
        contentList.addView(sectionText("当前:第${engine.year}学年 · 第${engine.term}学期 · 第${engine.termDay}天"))

        contentList.addView(sectionText("课程管理(提升学费)"))
        for (course in CourseType.values()) {
            val existing = engine.courses.firstOrNull { it.type == course }
            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(14, 10, 14, 10)
            card.setBackgroundResource(R.drawable.card_bg)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 8)
            card.layoutParams = lp

            val info = TextView(this)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            info.text = if (existing != null) {
                "${course.label} Lv.${existing.level} · 学费+${Math.round(existing.effect * 100)}%"
            } else {
                "${course.label} · ¥${GameEngine.formatMoney(course.cost)} · 需${course.requireBuilding.label}"
            }
            info.textSize = 13f
            info.setTextColor(resources.getColor(R.color.textLight))

            val btn = Button(this)
            btn.text = if (existing != null) "升级" else "开设"
            btn.textSize = 13f
            btn.setTextColor(resources.getColor(R.color.textLight))
            btn.background = resources.getDrawable(R.drawable.btn_primary)
            btn.stateListAnimator = null
            btn.isEnabled = engine.canOpenCourse(course)
            btn.setOnClickListener {
                sound.play("click")
                if (engine.openCourse(course)) {
                    sound.play("upgrade")
                    buildSchoolPanel()
                    refreshHud()
                } else {
                    showEvent("💸 资金或前置建筑不足!")
                }
            }

            card.addView(info)
            card.addView(btn)
            contentList.addView(card)
        }

        contentList.addView(sectionText("社团活动(提升满意度)"))
        for (club in ClubType.values()) {
            val opened = engine.clubs.contains(club)
            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(14, 10, 14, 10)
            card.setBackgroundResource(R.drawable.card_bg)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 8)
            card.layoutParams = lp

            val info = TextView(this)
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            info.text = if (opened) {
                "${club.emoji} ${club.label} · 已成立"
            } else {
                "${club.emoji} ${club.label} · ¥${GameEngine.formatMoney(club.cost)}"
            }
            info.textSize = 13f
            info.setTextColor(resources.getColor(R.color.textLight))

            val btn = Button(this)
            btn.text = if (opened) "已成立" else "成立"
            btn.textSize = 13f
            btn.setTextColor(resources.getColor(R.color.textLight))
            btn.background = resources.getDrawable(R.drawable.btn_gray)
            btn.stateListAnimator = null
            btn.isEnabled = !opened && engine.canOpenClub(club)
            btn.setOnClickListener {
                sound.play("click")
                if (engine.openClub(club)) {
                    sound.play("build")
                    buildSchoolPanel()
                    refreshHud()
                } else {
                    showEvent("💸 资金不足!")
                }
            }

            card.addView(info)
            card.addView(btn)
            contentList.addView(card)
        }

        contentList.addView(sectionText("成就"))
        var unlocked = 0
        for (a in engine.achievements) {
            if (a.unlocked) unlocked++
            contentList.addView(achievementRow(a))
        }
        contentList.addView(sectionText("已解锁成就 $unlocked / ${engine.achievements.size}"))
    }

    private fun achievementRow(a: Achievement): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(14, 8, 14, 8)
        row.setBackgroundResource(R.drawable.card_bg)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 6)
        row.layoutParams = lp

        val text = TextView(this)
        text.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        text.text = if (a.unlocked) "🏅 ${a.name}" else "🔒 ${a.name}"
        text.textSize = 13f
        text.setTextColor(resources.getColor(R.color.textLight))

        val desc = TextView(this)
        desc.text = a.desc
        desc.textSize = 11f
        desc.setTextColor(resources.getColor(R.color.textSoft))

        row.addView(text)
        row.addView(desc)
        return row
    }

    private fun sectionText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setTextColor(resources.getColor(R.color.btnGold))
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(4, 12, 4, 6)
        return tv
    }

    // ===== HUD =====
    private fun refreshHud() {
        moneyText.text = "💰 ${GameEngine.formatMoney(engine.money)}"
        studentText.text = "🎓 ${engine.students}/${engine.studentCapacity}"
        fameText.text = "⭐ ${engine.fame.toInt()}"
        happyText.text = "😊 ${engine.happy.toInt()}"
        dayText.text = "第${engine.term}学期·D${engine.day}"
        syncToRenderer()
        refreshBuildButtons()
        if (selectedBuilding != null && infoPanel.visibility == View.VISIBLE) {
            showSelection(selectedBuilding!!)
        }
    }

    private fun syncToRenderer() {
        gameView.renderer.setBuildings(ArrayList(engine.buildings))
    }

    // ===== 事件消息 =====
    private fun showEvent(msg: String, durationMs: Long = 2600) {
        eventHideRunnable?.let { handler.removeCallbacks(it) }
        eventText.text = msg
        eventText.visibility = View.VISIBLE
        val hide = Runnable { eventText.visibility = View.GONE }
        eventHideRunnable = hide
        handler.postDelayed(hide, durationMs)
    }
}
