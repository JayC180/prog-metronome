# juce_metronome

`prog-metronome` 的 C++/JUCE 桌面移植 —— 同样的节拍器逻辑，但出来是 **VST3 / AU 插件** 和 **跨平台独立 app**（Windows / macOS / Linux）。

本 README **不重复**节拍器本身的概念（rhythm builder、tracks、modulation 之类原项目已经写过）—— 只解释**工程结构** 和 **多平台 build 的运作机制**，因为你没接触过 JUCE。

---

## 1. JUCE 是什么，对应到你的 Kotlin/Compose 项目是哪些东西

JUCE 是 C++ 的一个 audio framework，可以理解为：

| Kotlin / Compose Multiplatform | JUCE 里对应的东西 |
|---|---|
| Compose Multiplatform UI | `juce_gui_basics` —— 自绘 widget，组件叫 `juce::Component`，每个组件自己 `paint(Graphics&)` + `resized()` |
| `Modifier` / `Theme` | `LookAndFeel` —— 全局样式表（颜色、字体、控件画法） |
| Gradle `commonMain` + 各平台 source set | **没有这个概念**。JUCE 一份 C++ 源码直接对所有平台编译，平台差异通过 `#if JUCE_MAC / JUCE_WINDOWS / JUCE_LINUX` 区分 |
| 音频/MIDI 抽象 | `juce_audio_processors` + `juce_audio_devices`（处理 host ↔ plugin、driver 抽象） |
| 项目配置（`build.gradle.kts`） | `CMakeLists.txt` |
| Gradle wrapper | CMake + 各平台 IDE 的 generator（Xcode / Visual Studio / Ninja） |

JUCE 一个项目本质上是一个 **plugin**，会自动生成多种"包装"（formats）：
- **Standalone** —— 独立 app（`.exe` / `.app` / Linux binary），自带 JUCE 的 audio device 选择 UI
- **VST3** —— `.vst3` bundle，能加载到 DAW（Cubase / Reaper / Ableton…）里
- **AU**（仅 macOS） —— `.component`，给 Logic / GarageBand 用

同一份代码，三种产物，区别只是 entry point 不同（CMake 帮你生成各自的 wrapper）。

---

## 2. 文件结构 —— Kotlin 视角下的对应

```
Source/
├── PluginProcessor.{h,cpp}     ← Plugin 入口。等价于 ViewModel 顶层 + audio engine 主类
├── PluginEditor.{h,cpp}        ← UI 入口。打开插件窗口时 JUCE 来 new 它
│
├── core/                       ← 与原项目的 commonMain 里 core 部分对应
│   ├── BeatNode.{h,cpp}        节拍树节点
│   ├── Rational.h              有理数（你 Kotlin 里也是）
│   └── TempoContext.h
│
├── builder/                    ← rhythm builder 状态机（原项目 builder 包的直译）
│   ├── TrackDraft.{h,cpp}      单条 track 的可编辑 state
│   ├── TrackItem.{h,cpp}       beats / brackets / repeats / tempo 节点
│   ├── TrackBuilderState.*     全局 state（所有 tracks + bpm + cursor + …）
│   ├── TrackBuilder.{h,cpp}    控制器：所有 mutate state 的方法都在这（addTrack / setBpm / enterBeat…）
│   ├── Interpreter.{h,cpp}     把 TrackDraft 编译成时间线（PrecomputedEvent 序列）
│   └── TapTempoCalculator.{h,cpp}
│
├── scheduler/                  ← 把时间线变成"什么时候播什么"
│   ├── PrecomputedEvent.h      Interpreter 的输出。每条 track 一个 InterpretResult
│   ├── ScheduledEvent.h        排进队列等待 dispatch 的事件
│   ├── StreamClock.{h,cpp}     单 track 时钟（管 loop、infinite repeat）
│   └── Transport.{h,cpp}       两线程引擎：scheduler thread 提前 150ms 排队，
│                               dispatcher thread 到点触发 audio engine
│
├── audio/                      ← 音频后端
│   ├── AudioEngine.h           接口：trigger(soundId, volume, atNanos)
│   ├── JuceAudioEngine.*       具体实现：JUCE Mixer + 采样回放
│   ├── SoundConfig.h / SoundInfo.h
│   └── SoundMap.*              soundId → AudioBuffer 的查找表
│
├── persistence/                ← JSON 存档 / 读档
│   └── ProjectSerializer.{h,cpp}   用 juce::var 树做（不是 kotlinx.serialization 那种基于反射的）
│
└── ui/                         ← 所有界面
    ├── MainComponent.*         根 component（约等于原项目的 MainScreen Composable）
    ├── TopBarComponent.*       顶栏：BPM、play、tap-tempo
    ├── BottomPanelComponent.*  底栏：节拍输入、选音色
    ├── TrackRowComponent.*     单条 track 的行（label + M/S chip + 内容 strip）
    ├── dialogs/RhythmDialogs.* 弹窗（音色选择器之类）
    ├── RhythmLookAndFeel.*     全局风格：颜色、字号、按钮形状（≈Compose 的 Theme）
    ├── RhythmColors.h / ThemeConfig.h
    └── UiHelpers.{h,cpp}       小复用组件（chip、按钮等）
```

### Audio thread 的约束（重要）

JUCE 的 audio callback 跑在一个 **实时线程**上 —— 这条线程里**不能**做：
- 内存分配（`new` / `std::make_shared` / push 进会扩容的 `vector`）
- 锁等待（`std::mutex::lock` 如果可能 block 就别用）
- 任何 syscall（文件 IO、log、网络…）

所以 `Transport` 故意把"准备 events"放在普通 scheduler 线程，audio 线程只做最快的事（直接读 ring buffer 之类）。改 audio path 时记得这条。

---

## 3. CMakeLists.txt 速读

`build.gradle.kts` 的对应物，只有 ~100 行。三段就懂：

```cmake
# 段 1：拉 JUCE。
# 优先用本地 ~/JUCE_local（开发机快），CI 上没这个目录就 FetchContent 拉 8.0.13。
add_subdirectory(...)

# 段 2：声明 plugin target，FORMATS 这一行决定输出哪些产物
juce_add_plugin(RhythmEngine
    FORMATS VST3 AU Standalone        # ← 关键。要 LV2/AAX 也加这里
    BUNDLE_ID com.elsasystems.rhythmengine
    PLUGIN_MANUFACTURER_CODE Elsa
    PLUGIN_CODE Rhy1
    IS_SYNTH TRUE
    COPY_PLUGIN_AFTER_BUILD TRUE      # ← 编译完自动装到系统插件目录（DAW 能直接看到）
    ...)

# 段 3：列源码 + 链 JUCE modules
target_sources(RhythmEngine PRIVATE Source/...)
target_link_libraries(RhythmEngine PRIVATE
    juce::juce_audio_utils            ← 这些就是 JUCE 的"package"
    juce::juce_gui_basics
    ...)
```

JUCE 的 **module** 就是它的 package 系统 —— 每个 module 一个文件夹，按需链。`juce_dsp` 是滤波器/FFT 之类；`juce_gui_extra` 是 webview 之类；我们只用必要的几个。

---

## 4. 怎么 build

### 本地（推荐）

需要 **CMake ≥ 3.22** 和平台编译器：

```bash
# 三平台命令一致 ——
cmake -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build --target RhythmEngine_Standalone
# 想要 VST3：  --target RhythmEngine_VST3
# macOS 想要 AU：--target RhythmEngine_AU
```

Generator 替换规则：
- macOS 想用 Xcode 调试 → `-G Xcode`，然后 `open build/RhythmEngine.xcodeproj`
- Windows → `-G "Visual Studio 17 2022" -A x64`（或用 Ninja）
- Linux → Ninja 或默认 Makefile 都行

产物路径：`build/RhythmEngine_artefacts/Release/{Standalone,VST3,AU}/`

`COPY_PLUGIN_AFTER_BUILD TRUE` 会同时把 VST3/AU 拷到系统插件目录（`~/Library/Audio/Plug-Ins/...` / `C:\Program Files\Common Files\VST3\` / `~/.vst3/`），所以本地一编 DAW 就能扫到。

### CI（GitHub Actions）

仓库根 `.github/workflows/build.yml` —— 三个并行 job：

| Job | runner | 装哪些依赖 | 产物 |
|---|---|---|---|
| `windows` | `windows-latest` | 自带 VS 2022 + CMake | `RhythmEngine.exe` + `RhythmEngine.vst3/` |
| `macos`   | `macos-latest`   | 自带 Xcode CommandLineTools | `RhythmEngine.app` + `.vst3` + `.component` |
| `linux`   | `ubuntu-latest`  | `apt-get install` 装 ALSA / JACK / X11 头文件 | `RhythmEngine` (binary) + `RhythmEngine.vst3/` |

每个 job 流程都是一样的三步：

```
checkout → cmake -B build → cmake --build → upload artifact
```

只有"装依赖"那一步因平台而异。这就是为什么 CMake 在跨平台 C++ 圈是事实标准 —— **一份 build 脚本各平台通吃**，CI 配置里几乎没有平台特定逻辑。

触发条件：
- push 到 `main`（每次提交都自动跑）
- push tag `v*`（用来打 release）
- 任何 pull request
- 手动 `workflow_dispatch`（Actions 页面有按钮）

JUCE 的源码不在仓库里，CMake 第一次跑会用 `FetchContent` 从 GitHub 克隆 JUCE 8.0.13 —— 所以每个 CI job 第一次会多花 ~3 分钟拉 JUCE，后续如果加缓存能省掉。

### 签名

**目前所有产物都未签名**：
- macOS：用户下载后双击会被 Gatekeeper 拦，要 **右键 → 打开** 一次。要彻底干净需要 Apple Developer ID（99 美刀/年）+ 在 CI 加签名 secrets，以后再说。
- Windows：未签名 exe 会被 SmartScreen 提示"未知发布者"。可以忽略也可以以后买证书。

---

## 5. 这个移植和 Kotlin 原版的差异

主要是 **没有 commonMain/iosMain/androidMain 的概念**：一份 C++ 跑所有桌面平台，移动端这边没做（JUCE 也能做 iOS/Android 但工作量很大，等后面再说）。

业务逻辑层基本是 1:1 直译：
- `builder/` 跟原项目 `builder` 包对应
- `core/BeatNode` 等同原项目同名类
- `Interpreter` 输出 `PrecomputedEvent` 列表 —— 这是 JUCE 版独有的优化（提前编译时间线，audio 线程零分配）
- UI 是 **重写**的（Compose 没法跨到 C++/JUCE），但布局和交互参考了原项目

