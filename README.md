# 思存 · 便签收纳 (NotesApp)

一个极简的安卓原生便签 App，把脑子里的东西分三类收纳：

- **四象限归纳** —— 借鉴 [Einsen](https://github.com/Spikeysanju/Einsen) 的 Eisenhower 矩阵，按「重要 / 紧急」把事分到四个象限：
  - ① 重要 · 紧急
  - ② 重要 · 不紧急
  - ③ 不重要 · 紧急
  - ④ 不重要 · 不紧急
- **点子存放处** —— 随手存灵感（借鉴 [ANotes](https://github.com/lestec-al/a-notes) 的「便签 + 分类」收纳思路）。
- **未想清楚的事** —— 把还没想明白的念头先丢进来，想清楚再移到别处。

> 设计理念：不追求大而全，先把「归纳 / 收纳 / 暂缓」三件事做顺手。

## 技术栈

- **Kotlin** + 原生 View (XML) 布局
- **Material Components** 底部导航与对话框
- **Gson** 做本地 JSON 持久化（文件 `notes.json` 存于应用私有目录）
- 无后端、无联网，数据只在你手机本地，卸载即清空，隐私友好

## 功能一览

| 模块 | 能力 |
|------|------|
| 四象限归纳 | 每个象限可直接「＋」添加；便签可在象限间「移」、也可跨模块「移」 |
| 点子存放处 | 列表式收纳，添加 / 编辑 / 换色 / 删除 / 移动 |
| 未想清楚的事 | 同上，与「点子」共用一套干净的列表界面 |
| 通用 | 每条便签支持 7 种背景色、内联编辑、删除；数据自动保存 |

## 目录结构

```
NotesApp/
├── build.gradle                 # 根构建（AGP / Kotlin 插件版本）
├── settings.gradle              # 模块与仓库配置
├── gradle.properties
├── gradle/wrapper/              # wrapper 配置（缺 gradle-wrapper.jar，见下）
├── app/
│   ├── build.gradle             # 依赖：appcompat / material / gson
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/workbuddy/notes/
│       │   ├── Note.kt              # 数据模型 + 象限/模块常量
│       │   ├── ColorPalette.kt      # 便签可选颜色
│       │   ├── NotesRepository.kt   # 本地 JSON 读写
│       │   ├── Cards.kt             # 便签卡片视图
│       │   ├── Ui.kt                # 编辑弹窗（文本 + 选色）
│       │   ├── MainActivity.kt      # 底部导航容器
│       │   ├── QuadFragment.kt      # 四象限
│       │   └── ListFragment.kt      # 点子 / 未想清
│       └── res/                     # 布局 / 配色 / 主题 / 图标
├── LICENSE
└── README.md
```

## 构建与运行（需要 Android Studio）

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 SDK / Gradle，无需另装）。
2. `File → Open` 选择本仓库根目录（即包含 `settings.gradle` 的 `NotesApp` 文件夹）。
3. 等待 Gradle Sync 自动下载依赖。
   - 若弹出「Gradle wrapper 文件缺失，是否重新创建？」，**点确定**即可（本仓库未附带 `gradle-wrapper.jar`，Android Studio 会自动补全，不影响最终构建）。
4. 连上安卓手机（开启 USB 调试），点 ▶ Run；或 `Build → Build APK(s)` 导出 `.apk`。

## 安装到手机（vivo 等安卓）

- 用数据线 / 微信 / 邮件把生成的 APK 传到手机，点击安装。
- vivo 会提示「允许安装未知应用」，允许后继续；若提示风险，选「仍要安装」。
- 装完桌面出现「思存 · 便签收纳」图标，打开即用。

## 发布到你自己的 GitHub

```bash
cd NotesApp
git init
git add .
git commit -m "init: 思存便签收纳 v1.0"

# 在 github.com 新建一个空仓库，拿到 HTTPS 地址后执行：
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git branch -M main
git push -u origin main
```

之后在 GitHub 的仓库页面就能看到完整代码；别人也可以 `git clone` 后照上面的步骤构建。

## 借鉴来源

- [Einsen](https://github.com/Spikeysanju/Einsen) —— 四象限（Eisenhower）UI 与「按优先级归纳」理念
- [ANotes](https://github.com/lestec-al/a-notes) —— 便签 + 分类收纳、纯本地存储

## 许可

[MIT](./LICENSE)
