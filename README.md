# 小鹤双拼输入法 — IntelliJ Platform 插件

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-000000?logo=jetbrains)](https://plugins.jetbrains.com/plugin/ORG.LYMANSIX.IDEAVIM-SHUANGPIN)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2026.1+-blue?logo=intellijidea)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue?logo=kotlin)](https://kotlinlang.org/)

一个为 IntelliJ IDEA 量身打造的 **小鹤音形** 中文输入法插件。不依赖操作系统输入法，直接在 IDE 内完成中文输入——编辑器、Git Commit、Search Everywhere、终端、运行配置、设置搜索框等所有文本输入场景均支持。

> 虽然插件名叫 `ideavim-shuangpin`，但实际使用的码表是 **小鹤音形**（4 键编码），而非严格意义上的小鹤双拼（2 键编码）。插件本身对编码方案不敏感——只要码表是 `编码→词` 的格式就能工作。

## ✨ 特性

- 🈶 **中英文模式切换** — `Ctrl+Alt+\` 快捷键或状态栏点击切换
- ⌨️ **Shift 临时英文** — 按住 Shift 输入字母直接上屏英文，松开恢复中文
- 🔢 **数字选词** — 候选窗口按 `1`–`9` 直接选中，空格 / 回车选中第一个
- 📏 **四键自动上屏** — 码长达 4 字符后再按字母会自动提交首选词并开始新词
- 🀄 **中文标点替换** — 中文模式下 `,` `.` `;` `\` 等自动替换为 `，` `。` `；` `、` 等全角标点
- 🎯 **原生候选窗口** — 基于 IDEA 的 `LookupManager`，与其他补全弹窗风格一致
- 🌐 **场景支持** — 编辑器、Git Commit
- 🔄 **IdeaVim 联动** *(可选)* — 检测到 IdeaVim 时，进入/退出插入模式自动切换中/英文模式（可通过 `enableSmartSwitch` 设置关闭）

## 📦 安装

1. 打开 IntelliJ IDEA → `Settings` → `Plugins` → `Marketplace`
2. 搜索 `Xiaohe IME`（或 `ideavim-shuangpin`）
3. 点击 `Install`，重启 IDE

> 如果插件尚未上架，可以从 [Releases](../../releases) 下载 zip 包，通过 `Install Plugin from Disk...` 安装。

## 🚀 使用

| 操作 | 说明 |
|---|---|
| 输入 `a`–`z` | 在中文模式下进入拼音组合，候选窗口弹出 |
| 按 `1`–`9` | 选中对应序号的候选词 |
| 按 `空格` 或 `回车` | 选中第一个候选词 |
| 按 `Shift` + 字母 | 临时输入大写英文字母（不进入拼音组合） |
| 按 `Esc` | 清空当前拼音组合，候选窗口关闭 |
| 按 `Backspace` | 删除拼音组合的最后一个字母；组合为空时恢复正常退格 |
| `Ctrl+Alt+\` | 切换中文 / 英文模式 |
| 点击状态栏 `中`/`英` | 同上，切换模式 |
| 输入标点 `,` `.` `\` 等 | 中文模式下自动替换为 `，` `。` `、` 等全角标点 |

### IdeaVim 用户

安装 IdeaVim 后，插件会自动识别 Vim 模式切换：

- 按 `<Esc>` 退出插入模式 → IME 自动切换到英文
- 按 `i` 进入插入模式 → IME 自动恢复到离开插入模式前的状态

此功能由 `ImeSettings.enableSmartSwitch`（默认开启）控制。如果你不希望自动切换，把它关掉即可。

## 🏗️ 架构

```
TypedAction.rawHandler
  │
  ▼
┌──────────────────────────────────┐
│ ImeTypeHandler  (核心分发)       │
│  · a-z: 进入 composing, 不落盘   │
│  · 1-9/空格/回车: 提交候选       │
│  · Shift+字母: 临时英文          │
│  · 其他: 提交后放行              │
└──────────────────────────────────┘
  │                      │
  ▼                      ▼
┌──────────────┐   ┌──────────────┐
│ ImeLookup    │   │ FlyPyDict    │
│ 候选窗口展示  │◄──│ 码表查询      │
└──────────────┘   └──────────────┘
       │
       ▼
┌──────────────┐
│ LookupManager│   ← IDEA 原生 API, 不修改 Document
└──────────────┘
```

关键设计：
- **拦截在 Document 之外** — 通过 `TypedAction.setupRawHandler()` 在字符到达 Document 之前拦截，拼音字母永远不会写进文档
- **候选提交即插入** — 通过 `WriteCommandAction.runWriteCommandAction` 直接将汉字写入 Document
- **IdeaVim 兼容** — 消耗按键时同步消耗 AWT KeyEvent，避免 IdeaVim 的 `jk → Esc` 等快捷键误触发
- **IdeaVim 可选集成** — 通过 `<depends optional="true">` 声明，未安装 IdeaVim 时相关代码完全不加载

## 🛠️ 开发

```bash
# 启动沙盒 IDE 调试插件（代码热更新）
./gradlew runIde

# 编译检查
./gradlew compileKotlin

# 运行测试（目前没有测试）
./gradlew check

# 验证插件兼容性
./gradlew verifyPlugin

# 构建可分发的 zip 包
./gradlew buildPlugin

# 发布到 JetBrains Marketplace
./gradlew publishPlugin
```

IDE 预置了三种 Run Configuration：`Run IDE with Plugin`、`Run Tests`、`Run Verifications`。

### 目录结构

```
src/main/kotlin/io/github/lymansix/ime/
├── action/
│   ├── HandlerInstaller.kt         生命周期钩子, 注册所有 handler
│   ├── ImeTypedHandler.kt          核心按键分发
│   ├── ImeBackspaceHandler.kt      退格处理
│   └── ImeEscapeHandler.kt         ESC 处理
├── dict/
│   ├── FlyPyDict.kt                码表加载与查询
│   ├── Candidate.kt                候选词数据类
│   └── Punctuation.kt              中英文标点映射
├── lookup/
│   └── ImeLookup.kt                候选窗口驱动
├── settings/
│   └── ImeSettings.kt              持久化设置
├── state/
│   └── ImeState.kt                 每编辑器的拼音组合状态
├── status/
│   └── ImeStatusWidget.kt          状态栏控件 + 切换 Action
├── vim/
│   └── VimImeInstaller.kt          IdeaVim 集成 (可选)
└── ImeBundle.kt                    国际化资源束
```

### 替换码表

将 `src/main/resources/dict/fly.txt` 替换为你自己的码表文件即可。格式要求：

```
编码   词1 [词2]
```

- 每行一个编码，空格分隔
- 第一列是编码（1-4 个小写字母）
- 后续列是该编码对应的词（最多 2 个，空格分隔）
- 编码列用空格填充到固定宽度（视觉对齐用，解析时会 trim）

## 🤝 贡献

欢迎提 Issue 和 PR！特别是：

- 码表改进
- 新编码方案支持
- 测试用例
- 文档完善

## 📄 License

[MIT](LICENSE)

---

**为什么叫 `ideavim-shuangpin` 却用音形码表？**

最初的项目设想是做一个纯小鹤双拼方案，但实际使用的码表是作者手头现有的小鹤音形码表（`fly.txt`，文件名源自"飞"字）。插件本身只把编码当作 1-4 个字母的不透明字符串做前缀匹配，对具体方案不敏感——理论上支持任何形如 `编码→词` 的码表。名字就保留下来了。
