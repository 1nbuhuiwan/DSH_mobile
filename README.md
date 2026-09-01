# 🚀 DSH 手机端 · DeepSeek Harness Remote 🚀

> **一个极轻量的安卓浏览器** —— 扫码连接 DeepSeek Harness，然后**在手机上直接操控 DeepSeek**。📲

本质上它就是一个浏览器（系统 `WebView`）：扫描 DSH Desktop 电脑端展示的配对二维码，得到一段链接，在 `WebView` 里打开，完成配对后即可从手机继续与 DeepSeek 对话，还能**选图直接发进 DeepSeek**。🖼️

---

## ⚠️ 开箱声明 · The Cosmic Disclaimer (请务必阅读)

> 🛑 **这 是 一 个 纯 "vibecoding" 项目。** 🛑

本项目由 **AI 辅助/随性编码（vibecoding）** 产生，追求的是一种"好玩、能跑、看得懂"的状态，**不是为了严肃工程而设计**。

**极度不建议**将它用于：任何**大型、严肃、生产级、对稳定性/安全/合规有要求的工程项目**中（比如医疗、金融、基础设施、企业核心链路等）。它存在：

- ❌ 未充分测试的边界情况；❌ 未做安全审计的代码路径；❌ 硬编码/本机相关的配置；❌ 依赖当前 DeepSeek Harness 内部实现（容易随上游变化而失效）；❌ 各种"能用但未必稳"的实现。

**作者对任何直接或间接的使用后果（数据丢失、安全漏洞、业务故障、金钱损失、精神损失…）概不负责。** 用之前请三思；用出问题，**我不负责**。🙏

> 换句话说：**拿它当玩具、当参考、当学习材料 —— 都可以；拿它上生产 —— 风险自担。** 😅

---

## ✨ 特性

- **📡 扫码连接**：首页大按钮，调起相机扫描 DSH Desktop 的配对二维码，一键接入电脑端的 DeepSeek 会话。
- **🌐 轻量浏览器**：顶栏显示当前项目 / 会话名，带前进 / 后退 / 刷新 / 主页 / 扫码；任意 `http/https` 页面。
- **🖼️ 发送图片**：在 DeepSeek 聊天输入框左侧注入醒目的「+」按钮。选图后弹出**圆角输入框**（可写描述、可留空），确认后把**图片 + 文字**直接发进 DeepSeek 会话。实现上**完全绕开网页收图 UI**，直连 Harness 会话接口（详见下文）。
- **🔔 任务完成通知**：DeepSeek 生成结束（回合完成）时，手机端弹出**系统通知**，点击回到应用；Android 13+ 首次启动会动态申请通知权限。
- **✍️ 手动输入地址**：连接页也可直接输入局域网地址或网址。
- **💾 会话保持**：Cookie / Web 存储持久化，重开应用自动恢复之前连接的会话；返回键退出再进也不会空白。
- **🌗 深色 / 浅色**：跟随系统，配色即 DeepSeek / DSH 的设计令牌。
- **📐 edge-to-edge 适配**：依据系统状态栏/导航栏 insets 自动留白，顶部不压状态栏、底部不贴边、软键盘弹出时输入框自动上抬。
- **🎬 系统动效 + 触感**：主按钮按压抬升、扫码页底部滑入/滑出、扫码取景框扫描线、注入按钮弹入动画、全局 ripple；扫码成功、按钮按压、附件选择均有振动反馈。

---

## 📥 下载 App（APK）

两种方式获取可安装的 APK：

### 方式一：GitHub Releases（推荐，分享给他人）🌟
> 本仓库已配置 **CI**：当你打 `v*` 标签（如 `v1.1.1`）时，会自动**构建并签名** Release APK，并发布成 **GitHub Release**。

1. 打开仓库的 **Releases** 页：`https://github.com/1nbuhuiwan/DSH_mobile/releases`
2. 找到最新版本（如 `v1.1.1`），下载 **`app-release.apk`**（正式、已签名、体积最小）。
3. 在手机上安装该 APK 即可（Android 需允许"未知来源/外部安装"）。

> 第一次发布：在本地执行 `git tag v1.1.1 && git push origin v1.1.1`，CI 会自动构建并发布 Release。

### 方式二：本地构建
用 Android Studio 打开本仓库运行，或 `./gradlew assembleDebug`；生成的 `app-debug.apk` 也可安装（调试用）。

> 说明：Release APK 用**构建时临时生成的密钥**签名，不同版本签名不同，升级需先卸载再装；如需长期一致签名，请改用 GitHub Secret 存 keystore（见 `.github/workflows/android.yml`）。

---

## 🎯 它是怎么工作的

DSH Desktop（桌面端）运行时会开一个局域网桥接服务（监听 `0.0.0.0`，也可经互联网隧道访问）。桌面端「连接移动设备」页会生成一个**二维码**，内容是一段配对链接：

- 🏠 **WiFi 模式**：`http://<电脑局域网IP>:<端口>/pair?token=...`
- 🌍 **互联网模式**：`https://<隧道域名>/pair?token=...`

手机扫码后，代码在本应用的 `WebView` 中打开该链接：

```
[手机 App] 扫码 ─► 得到配对链接 ─► WebView 打开 /pair?token=...
                                          │
[桌面端]   弹出「批准此手机」 ─► 用户点「允许」
                                          │
[手机 App] 收到 Cookie 并跳转 / ─► 加载 DSH 应用 ─► 开始操控 DeepSeek
```

所以手机端**只需要一个能扫码、能加载网页的极轻浏览器**，所有业务逻辑都在桌面端完成，手机端几乎不额外占内存。😎

---

## 🎨 设计（与 DeepSeek / DSH 一致）

直接取用 DSH Desktop 的 CSS 设计令牌：

| 令牌 | ☀️ 亮色 | 🌙 暗色 |
|---|---|---|
| 背景 `--bg` | `#FFFFFF` | `#141416` |
| 表面 `--surface` | `#FFFFFF` | `#1D1D20` |
| 面板 `--panel` | `#F7F8FA` | `#202023` |
| 正文 `--ink` | `#18191C` | `#F5F5F6` |
| 次要 `--muted` | `#81858C` | `#95979D` |
| 分隔 `--line` | `#E5E7EB` | `#303034` |
| 品牌 `--brand` | `#4D6BFE` | `#6F86FF` |

扫码取景框、按钮、卡片均按这套品牌蓝 + 圆角风格绘制。

---

## ⚡ 轻量化措施

- **只用系统 `WebView`**：不引入第三方浏览器内核（Gecko/Blink 独立进程），内存开销最小。
- **极少的第三方库**：仅 `androidx.*`（AppCompat / Activity / CameraX）与 `zxing:core`（纯 Java、约 1MB、无模型下载、不依赖 Google Play Services）。
- **扫码页轻量**：CameraX 预览 + ZXing 解码；帧率节流（350ms）、后台线程解码、单帧用完即 `close()`，避免 YUV 缓冲泄漏。
- **取景覆盖层零合成**：用四块矩形"压暗四周、中央透明"，不启用 PorterDuff 图层合成，避免额外离屏缓冲。
- **原生库精简**：`abiFilters` 仅保留 `arm64-v8a / armeabi-v7a / x86_64`；release 开启混淆 + 资源裁剪。
- 关闭缩放控件、关闭多窗口，减少 WebView 内建开销。

---

## 🔧 构建

### 方式一：Android Studio（推荐，最省心）🌟

用 Android Studio 打开本仓库根目录（`dsh-mobile/`），点 ▶ 运行即可。`local.properties` 与 debug 签名由 Android Studio 自动生成。

### 方式二：命令行

```bash
# 需要 JDK 17 + Android SDK（compileSdk 36 / targetSdk 36）
export JAVA_HOME=<你的 JDK17 路径>
export ANDROID_HOME=<你的 Android SDK 路径>
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 本仓库还带一个本机专用的 `./build.sh`（指向本机 `/Users/Admin/android-toolchain` 工具链），**仅为本人开发机方便，换机器直接用 Android Studio / gradlew 即可**。

---

## 📲 使用方法

1. **电脑端**：启动 DSH Desktop → 打开「连接移动设备」。
2. 选择模式：**🏠 WiFi 连接模式**（手机/电脑同一 WiFi，低延迟）或 **🌍 互联网连接模式**（经 cloudflared / pinggy 隧道，4G/5G/任意网络）。
3. 屏幕出现**二维码**。
4. **手机端**：打开本 App → 点「扫码连接」→ 对准二维码。
5. 电脑上点「**允许**」。
6. 手机自动载入 DSH 应用，开始操控 DeepSeek。点聊天框左侧 **「+」** 还能**选图发送**。📸

> 📌 需要相机权限（扫码）与网络权限（浏览）。局域网配对使用明文 HTTP，App 已允许 cleartext（仅局域网/配对场景）。

---

## 🔐 权限

| 权限 | 用途 |
|---|---|
| `CAMERA` | 扫描配对二维码 |
| `INTERNET` | 浏览网页 / 连接 DSH |
| `ACCESS_NETWORK_STATE` | 网络状态判断（预留） |
| `VIBRATE` | 触感反馈 |

---

## 📂 目录结构

```
dsh-mobile/
├── README.md                    # 本项目文档（你正在看）
├── LICENSE                      # MIT
├── docs/
│   └── 发图片功能开发总结.md      # 发图片一役的完整排障 + 最终方案
├── build.gradle.kts              # 顶层：AGP 8.9.2 + Kotlin 1.9.24
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradle/wrapper/     # Gradle Wrapper 8.11.1
└── app/
    ├── build.gradle.kts          # 依赖与轻量化配置
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/dsh/mobile/
        │   ├── MainActivity.kt         # 首页 + WebView 浏览器 + 发图通道
        │   ├── ScanActivity.kt         # CameraX + ZXing 扫码
        │   ├── ScannerOverlayView.kt   # 取景框覆盖层（含扫动扫描线）
        │   └── Haptics.kt              # 轻触/成功两档振动（兼容 API 24+）
        └── res/
            ├── layout/                 # activity_main / activity_scan / dialog_send_image
            ├── values/ & values-night/ # DeepSeek 配色（亮/暗）
            ├── drawable/               # 矢量图标与按钮样式
            └── xml/network_security_config.xml
```

---

## 🧭 已知限制

- **只能发图片**：Harness 附件模型是图片专用（`image/png|jpeg|webp|gif`）。Word / PPT 等文档没有对应接收机制，无法通过本 App 发送（这是 Harness 能力限制）。
- **图片会被压缩成较小 JPEG**：受桌面端 `/api/rpc` 请求体 **64KB** 上限；要发高清原图需调大桌面端上限。
- 扫码解码作用于整帧（取景框仅视觉引导），未按框裁剪。
- `target=_blank` 在本 `WebView` 内打开（关闭多窗口，省内存）。
- 浏览器为最小实现，无标签页/下载管理（这些会显著增加内存占用，有意省略）。

---

## 🔢 版本规范（Versioning）

采用 **语义化版本 `MAJOR.MINOR.PATCH`**（当前 `1.1.1`）。**后续有任何改进建议，请把版本号向后延续**：

- 🏗️ 有破坏性大改动 → 升 `MAJOR`（如 `2.0.0`）
- ✨ 新增功能 → 升 `MINOR`（如 `1.1.0`）
- 🐛 修 bug / 小优化 → 升 `PATCH`（如 `1.0.1`）

同时同步更新 `app/build.gradle.kts` 里的 `versionCode`（每次递增）与 `versionName`。

---

## 🚨 宇宙级安全声明（再强调一次）🌟

**这是一个纯 vibe-coding 项目。**

- 它**没有**经过系统性测试、**没有**安全审计、**没有**工程级设计评审；
- 它**直接依赖**当前 DeepSeek Harness 的**内部实现**（如 `/api/rpc`、`session.prompt`、`activeSession` 全局变量、`ComposerAttachments` 等），上游一变就可能失效；
- 代码里存在**为本地/特定环境**服务的配置与脚本。

**因此：请勿**将其用于任何严肃、大型、生产级、对稳定性/安全/合规有要求的地方。**出任何问题，作者概不负责。** 🙅‍♂️

> 仅供学习、参考、娱乐。用它做点什么之前，先问自己一句："我承担得起风险吗？" 😉

---

## 🤝 开源协作（License）

本项目以 **MIT License** 开源（见 [LICENSE](LICENSE)），可自由使用 / 修改 / 分发。

> **作者**：@1nbuhuiwan（纯 vibe-coding 产物，欢迎学习 / 参考 / 娱乐，谨慎用于生产）。🐾
>
> 若你 Fork 并改进，欢迎保持友好；但**请把"宇宙级免责声明"一起带上** 😄（原作者不背锅，改进者同样请自担风险）。

---

## 🙏 致谢

感谢 **DeepSeek / DeepSeek Harness** 提供的强大能力，以及一路上帮我验证、踩坑、debug 的（人形）测试员。🙌

> —— by **1nbuhuiwan** 🐾
