# 🏫 校长我来当2

竖屏 3D 卡通大学模拟经营游戏(Kotlin / OpenGL ES 2.0 / 原生 Android,无第三方依赖)。

## 🎮 玩法

你是一所大学的新任校长,目标是把校园建设成一流学府:

- 🏗️ **建造设施**:教学楼、宿舍、食堂、图书馆、实验室、体育馆、花园、操场
- 💰 **经济循环**:学生交学费 → 建造/升级设施 → 吸引更多学生
- ⭐ **声望与满意度**:设施影响声望和满意度,声望高新生多,满意度低学生会转学
- 🎲 **随机事件**:政府拨款、校友捐赠、设备老化、学生投诉……
- 👆 **3D 交互**:点击建筑查看/升级/拆除,单指拖动旋转视角

## 🛠 构建

```bash
./gradlew assembleRelease
```

GitHub Actions 会在每次 push 到 `main` 时自动构建并发布 Release(APK 直接可安装)。

## 📁 结构

```
app/src/main/java/com/principal/school2/
├── MainActivity.kt          # 主界面:竖屏 UI + 游戏循环
├── GLGameView.kt            # GL 视图:触摸旋转 + 射线拾取
├── game/
│   ├── GameEngine.kt        # 核心模拟:经济/学生/声望/事件
│   ├── Building.kt          # 建筑实例
│   └── BuildingType.kt      # 建筑类型定义
└── render/
    ├── GameRenderer.kt      # OpenGL ES 2.0 渲染器
    ├── CampusMeshBuilder.kt # 卡通建筑拼装
    └── ShapeBuilder.kt      # 程序化几何体
```
