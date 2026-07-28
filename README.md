# 🎀 美乐蒂猜猜乐 (Melody Guess Game)

一个可爱的海龟汤式AI猜谜游戏！**专猜「网络热梗 / 流行语」** —— 美乐蒂会通过提问来猜出你心中想的是哪个梗，遇到不确定的新梗还会**真去联网搜索**再判断~

## 🎮 游戏特色

- **美乐蒂主题界面** - 可爱的My Melody形象贯穿全程
- **AI智能猜梗** - 接入硅基流动API，以「网络热梗/流行语」为主题进行海龟汤式推理
- **真正的联网能力** - 通过 Function Calling 调用 `web_search` 工具，遇到新梗/不确定梗时实时联网搜索（默认 DuckDuckGo，可配 Brave 密钥更准）
- **动态表情** - 每次回答后美乐蒂的表情都会变化
- **5种交互选项** - 或许是/或许不是/是/不知道/否
- **颜文字对话** - 每句话都带有可爱颜文字 (≧∇≦)/

## 📱 游戏流程

1. **开始界面** - 显示欢迎语，点击"开始游戏"
2. **猜谜过程** - AI从「梗分类/出圈平台/情绪用途/载体类型」等维度提问，逐步缩小范围（约8题后首次猜测，必要时联网搜索）
3. **猜测确认** - AI给出猜测答案，玩家确认"对"或"不对"
4. **结果展示** - 猜对显示开心表情，猜错显示遗憾表情

## 🔧 构建APK

### 方法一：使用 Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开此项目文件夹 (`MelodyGuessGame`)
3. 等待 Gradle 同步完成
4. 菜单 → Build → Build Bundle(s) / APK(s) → Build APK(s)
5. APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

### 方法二：使用 GitHub Actions（自动构建）

1. 将项目推送到 GitHub 仓库
2. 进入仓库的 Actions 页面
3. 选择 "Build APK" 工作流，点击 "Run workflow"
4. 构建完成后在 Artifacts 中下载 APK

### 方法三：命令行构建

**前置要求：**
- JDK 17+
- Android SDK (API 34)
- 环境变量 `JAVA_HOME` 和 `ANDROID_HOME` 已设置

```bash
# Windows
gradlew.bat assembleRelease

# Linux/Mac
chmod +x gradlew
./gradlew assembleRelease

# Release APK 位置
# app/build/outputs/apk/release/app-release-unsigned.apk
```

### 快速构建脚本（Windows）

双击运行 `build_apk.bat`，脚本会自动：
- 下载 Android 命令行工具
- 安装必要的 SDK 组件
- 执行 Gradle 构建

> ⚠️ 需要先安装 JDK 17：[下载地址](https://adoptium.net/temurin/releases/?version=17)

## ⚙️ API 配置

### 默认配置
应用已预置硅基流动 API 密钥。

### 修改 API 密钥 / 搜索密钥
进入游戏后，点击右上角 ⚙️ 设置按钮，可修改两项：
- **硅基流动 API 密钥**（必填，已内置）
- **搜索 API 密钥（可选）**：留空则默认使用免密钥的 DuckDuckGo 联网搜索；填入 **Brave Search API** 密钥后，中文梗检索更精准（获取地址：https://brave.com/search/api/ ，免费额度足够个人玩）

### API 说明
- **服务商**: 硅基流动 (SiliconFlow)
- **接口地址**: `https://api.siliconflow.cn/v1/chat/completions`
- **模型**: `Qwen/Qwen2.5-72B-Instruct`（更强梗知识 + 支持工具调用）
- **联网搜索**: 通过 `web_search` Function Calling 实现，应用内真实发起 HTTP 搜索
- **更换模型**: 修改 `MainActivity.java` 顶部的 `MODEL` 常量即可（需为硅基流动支持工具调用的模型，如 `deepseek-ai/DeepSeek-V3`）

## 📁 项目结构

```
MelodyGuessGame/
├── app/
│   ├── src/main/
│   │   ├── java/com/melodyguess/
│   │   │   └── MainActivity.java    # 主活动 & 游戏逻辑
│   │   ├── res/
│   │   │   ├── drawable/            # UI 资源（按钮、背景等）
│   │   │   ├── layout/              # 布局文件
│   │   │   └── values/              # 字符串、颜色、样式
│   │   ├── assets/images/           # 美乐蒂表情图片
│   │   └── AndroidManifest.xml      # 应用清单
│   └── build.gradle                 # 应用级构建配置
├── .github/workflows/build.yml      # GitHub Actions 自动构建
├── build.gradle                     # 项目级构建配置
├── gradle.properties                # Gradle 属性
├── gradlew / gradlew.bat            # Gradle 包装器
└── build_apk.bat                    # Windows 一键构建脚本
```

## 🖼️ 图片资源说明

| 文件名 | 用途 |
|--------|------|
| melody_1~7.jpg | 游戏中随机轮换的表情 |
| melody_success.jpg | 猜对时显示 |
| melody_fail.jpg | 猜错时显示 |
| melody_default.xml | 默认占位图（矢量） |

## 📝 自定义修改

### 修改猜测频率
编辑 `MainActivity.java`:
```java
private static final int QUESTIONS_BEFORE_GUESS = 8; // 改为其他数字
```

### 更换 AI 模型
编辑 `MainActivity.java` 顶部的常量:
```java
private static final String MODEL = "Qwen/Qwen2.5-72B-Instruct";
```

### 修改系统提示词
编辑 `buildSystemPrompt()` 方法来自定义 AI 人格和对话风格。

## 📄 许可证

本项目仅供学习交流使用。

---

Made with 💖 by WorkBuddy
