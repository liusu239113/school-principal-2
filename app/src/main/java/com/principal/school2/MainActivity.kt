package com.principal.school2

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.principal.school2.game.Building
import com.principal.school2.game.BuildingType
import com.principal.school2.game.GameEngine

/**
 * 校长我来当2 - 主界面
 * 竖屏 3D 校园模拟经营
 */
class MainActivity : Activity() {

    private lateinit var engine: GameEngine
    private lateinit var gameView: GLGameView

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

    private var selectedBuilding: Building? = null

    private val handler = Handler(Looper.getMainLooper())
    private var eventHideRunnable: Runnable? = null

    // 每日结算(1 秒 = 1 天)
    private val dayRunnable = object : Runnable {
        override fun run() {
            engine.tickDay()
            refreshHud()
            handler.postDelayed(this, 1000)
        }
    }

    // HUD 刷新(0.5 秒)
    private val hudRunnable = object : Runnable {
        override fun run() {
            refreshHud()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = GameEngine()
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

        // 事件消息
        engine.eventListener = { msg -> showEvent(msg) }

        // 3D 点击拾取 → 选中建筑
        gameView.onBuildingSelected = { b -> onPick(b) }

        // 按钮事件
        findViewById<Button>(R.id.buildBtn).setOnClickListener { openBuildPanel() }
        findViewById<Button>(R.id.closeBuildBtn).setOnClickListener { closeBuildPanel() }
        findViewById<Button>(R.id.closeInfoBtn).setOnClickListener { clearSelection() }
        upgradeBtn.setOnClickListener {
            val b = selectedBuilding ?: return@setOnClickListener
            if (engine.upgrade(b)) {
                refreshHud()
                showSelection(b)
            } else {
                showEvent("💸 资金不足,无法升级!")
            }
        }
        demolishBtn.setOnClickListener {
            val b = selectedBuilding ?: return@setOnClickListener
            engine.demolish(b)
            clearSelection()
        }

        // 建造卡片
        buildBuildingCards()

        // 初始状态
        syncToRenderer()
        refreshHud()
        showEvent("🎓 欢迎新校长上任!这是你的大学!")

        // 启动循环
        handler.post(dayRunnable)
        handler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ===== 3D 拾取回调 =====
    private fun onPick(b: Building?) {
        if (b == null) {
            clearSelection()
            return
        }
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
                if (engine.build(type)) {
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
        closeSelectionAndBar()
        refreshBuildButtons()
        buildPanel.visibility = View.VISIBLE
    }

    private fun closeBuildPanel() {
        buildPanel.visibility = View.GONE
        buildBar.visibility = View.VISIBLE
    }

    private fun closeSelectionAndBar() {
        clearSelection()
        buildBar.visibility = View.GONE
    }

    private fun refreshBuildButtons() {
        for (i in 0 until buildingList.childCount) {
            val card = buildingList.getChildAt(i) as LinearLayout
            val btn = card.getChildAt(card.childCount - 1) as Button
            val type = BuildingType.values()[i]
            btn.isEnabled = engine.canBuild(type)
            val price = card.getChildAt(1) as TextView
            price.setTextColor(resources.getColor(R.color.btnGold))
        }
    }

    // ===== HUD =====
    private fun refreshHud() {
        moneyText.text = "💰 ${GameEngine.formatMoney(engine.money)}"
        studentText.text = "🎓 ${engine.students}/${engine.studentCapacity}"
        fameText.text = "⭐ ${engine.fame.toInt()}"
        happyText.text = "😊 ${engine.happy.toInt()}"
        dayText.text = "D${engine.day}"
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
    private fun showEvent(msg: String) {
        eventHideRunnable?.let { handler.removeCallbacks(it) }
        eventText.text = msg
        eventText.visibility = View.VISIBLE
        val hide = Runnable { eventText.visibility = View.GONE }
        eventHideRunnable = hide
        handler.postDelayed(hide, 2600)
    }
}
