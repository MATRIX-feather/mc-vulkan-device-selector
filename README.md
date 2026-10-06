# Vulkan Device Select (vkselect)

面向 **Minecraft 26.3** 的客户端模组，用来指定游戏下次启动时优先使用的 **Vulkan GPU**。
同时支持 **Fabric** 与 **NeoForge** 两个加载器，两边的代码都来自同一份 `common/src/main`。

26.3 引入了实验性的 Vulkan 渲染后端，但 Mojang 没有提供任何设备选择配置：游戏在
`com.mojang.renderpearl.backend.vulkan.VulkanBackend#findPhysicalDevice` 里枚举所有物理设备，
只挑「第一个可用」的设备，并且仅粗略地倾向独显（dGPU）。本模组通过客户端指令与图形界面让玩家
自己决定使用哪一块 GPU（包括 llvmpipe 这类 CPU 软件渲染器，方便调试）。选择会写入配置文件并在
**下次启动**时生效；如果选中的不是当前这块 GPU，模组还会弹出「重启 Minecraft?」提示，引导玩家退出
游戏并从启动器重新启动。

---

## AI 提示
本项目全程使用AI生成（包括项目的搭建和代码），生成过程中的决策和最后的开源许可由我处理。
代码经过人工粗略检查。

## 功能

### 客户端指令 `/vkselect`

| 指令 | 说明 |
| --- | --- |
| `/vkselect list` | 列出当前所有可用的 Vulkan 设备（含软件渲染器） |
| `/vkselect select <keyword>` | 按 keyword 匹配设备，把**第一个**匹配到的设备保存下来，下次启动时使用 |

`list` 的每条格式为：

```
[<index>] <isCurrent?> <device name> <driver> <driver version>
```

例如：

```
[0] * NVIDIA GeForce RTX 5060 Ti (dGPU) | NVIDIA 580.65.06
[1]   AMD Ryzen 7 9700X 8-Core Processor (RADV RAPHAEL_MENDOCINO) (iGPU) | radv 25.2.0
[2]   llvmpipe (LLVM 20.1.8, 256 bits) (software) | llvmpipe 25.2.0
```

- `*` 表示当前这次游戏正在使用的设备（其它条目为空格）。
- `<index>` 是 `vkEnumeratePhysicalDevices` 的枚举序号。
- `<driver>` / `<driver version>` 来自 `VkPhysicalDeviceDriverProperties` 与
  `VkPhysicalDeviceProperties.driverVersion`，NVIDIA / Intel 等厂商特有的版本编码会被解码成
  `580.65.06` 这种可读形式。

`select` 的 keyword 会自动补全：补全项就是 `<device name>`（完整设备名），匹配规则是先做
大小写不敏感的全等匹配，再退化为「包含」匹配，因此 `/vkselect select nvidia`、`/vkselect select radeon`
这类短关键字也能用；也可直接输入补全出来的完整名字。

指令会通过原版的指令反馈（聊天栏）告诉玩家结果；**只有指令**会在聊天栏留下系统消息，图形界面不会
（见下面 Overlay 一节）。

### Overlay（图形界面）

界面布局与 `新overlay示意图.png` 一致：

```
┌────────────────────────────────────────────┐
│            Vulkan 设备选择          [配置] │
│  ┌──────────────────────────────────────┐  │
│  │           <device name 1>            │▓ │
│  │- - - - - - - - - - - - - - - - - - - │  │
│  │           <device name 2>            │░ │  ← 滚动菜单（固定 4 行 + 右侧滚动条）
│  │- - - - - - - - - - - - - - - - - - - │  │
│  │           <device name 3>            │  │
│  └──────────────────────────────────────┘  │
│        当前设备：<device name>             │
│                                        │   │
│   [ 取消 ]                     [ 确认 ]    │
└────────────────────────────────────────────┘
```

- 原来的 Dropdown 换成了**滚动菜单**：固定显示 4 行，超出部分用右侧滚动条浏览；支持鼠标滚轮与
  拖动滚动条滑块，行间是与示意图一致的虚线分隔。
- 滚动条直接使用原版贴图（`minecraft:widget/scroller` / `widget/scroller_background`，与
  `AbstractScrollArea#extractScrollbar` 相同的画法与尺寸），并把鼠标指针切换成原版的
  手型/上下拖动样式，不再自绘色块。
- 右上角新增 **`配置`** 入口，打开配置页面（见下）。
- 选中项以绿色高亮并带 `>` 前缀；`确认` 写入配置文件，**下次启动游戏时生效**（界面里也会提示需要重启）。
- 界面**不会**往聊天栏发系统消息：选中状态、当前设备、"下次启动生效"的提示都在页面里，反复打开确认
  不会刷屏；只有 `/vkselect` 指令会通过指令反馈在聊天栏给出结果。
- 如果选中的设备与**当前正在使用的 GPU 不同**，`确认` 之后会再弹出「重启 Minecraft?」提示，见下一节。

设备名往往很长（例如 `AMD Ryzen 7 9700X 8-Core Processor (RADV RAPHAEL_MENDOCINO)`），所以界面文字采用
**放得下就居中、放不下就在框内来回滚动**的方式（`GuiText#drawFitted`）：滚动带缓动、按框裁剪，绝不会
溢出到面板外面；标题、列表条目、"当前设备"和提示行都走同一套逻辑。滚动速度由
`GuiText.SCROLL_PIXELS_PER_SECOND`（默认 40 px/s）控制，面板宽度上限由
`VulkanDeviceSelectScreen.MAX_PANEL_WIDTH`（默认 360）控制，两者都可以直接调。

打开 Overlay 的三个入口：

1. **主界面（Title Screen）**：左上角的 `Vulkan device` 按钮。
2. **游戏菜单（Pause Screen）**：同样的按钮，可通过配置项 `showInPauseMenu` 关闭。
3. **模组菜单**：Fabric 上通过 ModMenu 的 `Config` 按钮，NeoForge 上通过模组列表里对应的配置按钮
   （`IConfigScreenFactory`）。

### 重启提示「重启 Minecraft?」

选择了一块**与当前正在运行的 GPU 不同**的设备时，`确认` 之后会弹出 `confirm截图.png` 里的提示页
（`RestartRequiredScreen`）：

```
┌────────────────────────────────────────────┐
│              Restart Minecraft?            │
│                                            │
│  You have changed the preferred GPU for    │
│  Minecraft, this would need a manual       │
│  restart.                                  │
│                                            │
│  Click "Confirm" to close the game, then   │
│  launch the game again from the launcher.  │
│                                            │
│  [ I'll restart later ]      [ Confirm ]   │
└────────────────────────────────────────────┘
```

- `Confirm`：调用原版「退出游戏」同一条路径（`Minecraft#stop()`），正常保存并关闭游戏，然后由玩家
  从启动器重新启动 —— 模组不会自己重启进程，也不会去启动一个新的 Minecraft。
- `I'll restart later`（或 `Esc`）：选择已经保存，继续当前这局游戏，下次启动时才生效。
- 提示只在**真的需要重启**时出现：设备没变、或选的正是当前这块 GPU 时不会打扰（`needsRestart()`）。
- `/vkselect select <keyword>` 选到不同设备时也会弹出同一个提示（此时没有父界面，`稍后重启` 回游戏）。
- 「确认」按钮有 0.5 秒（10 tick）的保护延迟（原版 ConfirmScreen 对破坏性按钮的做法）：因为它是被选择页的
  `确认` 点击打开的，双击的第二次点击不会误触「关闭游戏」。

### 配置页面

从 Overlay 右上角的 `配置` 按钮进入（布局见 `config示意图.png`）：标题、每个配置项一行（选项标题在上、
`开/关` 开关在下）、底部 `返回`。开关**改动即写盘**，不需要额外保存；`返回` 回到设备选择页面。

| 配置项 | 说明 |
| --- | --- |
| 在主界面插入按钮 | 对应 `showInMainMenu`，控制主界面入口按钮 |
| 在游戏菜单插入按钮 | 对应 `showInPauseMenu`，控制暂停菜单入口按钮 |

> 入口按钮的显隐在下次打开对应界面时生效。`enabled`（总开关）与 `logDeviceSelection` 目前仍只通过
> 配置文件或 `-Dvkselect.disabled=true` 控制，如需也做成开关，只要在 `VkSelectConfigScreen` 的行列表里
> 再加一行即可。

### 配置文件 `config/vkselect.json`

```json
{
  "enabled": true,
  "selectedDevice": "NVIDIA GeForce RTX 5060 Ti",
  "showInMainMenu": true,
  "showInPauseMenu": false,
  "logDeviceSelection": true
}
```

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关；也可以用 `-Dvkselect.disabled=true` 临时禁用而无需改文件 |
| `selectedDevice` | `""` | 下次启动要使用的设备全名；为空表示完全沿用原版选择逻辑 |
| `showInMainMenu` | `true` | 是否在主界面添加入口按钮（也可在配置页面里切换） |
| `showInPauseMenu` | `true` | 是否在游戏菜单（暂停界面）添加入口按钮（也可在配置页面里切换） |
| `logDeviceSelection` | `true` | 选择设备时是否把所有设备名写进日志，便于排查 |

指令 `select` 与界面 `Confirm` 都会写入这个文件；也可以手动编辑。

---

## 依赖

| 加载器 | 必需 | 可选 |
| --- | --- | --- |
| Fabric | [Fabric API](https://modrinth.com/mod/fabric-api)（`0.161.0+26.3`，`fabricloader >= 0.19.5`） | [ModMenu](https://modrinth.com/mod/modmenu)（`21.0.0` 及以上，仅用于模组菜单入口） |
| NeoForge | NeoForge 自身（`26.3.0.x-beta`） | 无 |

两边都**没有**引入额外的第三方库：Fabric 侧只用 `fabric-command-api-v2` 与 `fabric-screen-api-v1`，
NeoForge 侧只用 loader 自带的 API。Minecraft 26.3 的客户端 jar 已经不混淆，因此也不需要
Yarn / Intermediary / refmap。

---

## 环境要求

- Minecraft **26.3**（Java 版客户端）
- Java **25**（Mojang 随 26.3 分发的运行时）
- 显卡驱动提供 Vulkan 1.2+（`vkEnumeratePhysicalDevices` 可用即可，模组自身只做枚举）

---

## 使用前提

本模组只负责「选哪块 Vulkan 设备」，因此需要游戏本身启用了 Vulkan：

`选项 > 显示设置 > 图形 API > 偏好 Vulkan（实验性）`，然后重启游戏。

注意 26.3 的「默认（Default）」**不是** Vulkan，而是先尝试 OpenGL，只有在 OpenGL 不可用时才会退回 Vulkan；
所以保持「默认」时本模组的选择不会生效（Overlay 会在这种情况下用橙色文字提示）。

如果图形 API 仍是 OpenGL / 默认，游戏根本不会创建 Vulkan 实例，本模组的选择也不会生效；
此时 Overlay 里会用橙色文字提示这一点，`F3` 右上角也能看到真正的后端。

---

## 实现原理

1. **设备枚举**（`VulkanDeviceEnumerator`）
   自己创建一个临时 `VkInstance`（macOS 上同时打开 `VK_KHR_portability_enumeration`）并调用
   `vkEnumeratePhysicalDevices` + `vkGetPhysicalDeviceProperties2`。因为不依赖游戏当前的渲染后端，
   所以在主界面、OpenGL 模式下也能列出设备。结果缓存 10 秒，避免指令补全时反复创建实例。

2. **启动时生效**（`VulkanBackendMixin`）
   `@Inject(method = "findPhysicalDevice", at = @At("HEAD"), cancellable = true)`：
   读取配置里的设备名 → 在当前实例枚举出的设备里匹配 → 通过反射调用原版的
   `checkDeviceSuitability` 确认这块设备能跑当前的 26.3 → 返回
   `new VulkanPhysicalDevice(handle)`，直接接管原版的选择结果。
   匹配不到、或设备不满足要求时**不取消**原版逻辑，只是打日志并退回原版行为，
   因此一个失效的配置最多是「不生效」，不会让游戏崩溃。

3. **入口注入**
   完全不额外混入原版 GUI 类：
   - Fabric：`ClientCommandRegistrationCallback` + `ScreenEvents.AFTER_INIT` + `Screens.getWidgets(Screen)`
   - NeoForge：`RegisterClientCommandsEvent` + `ScreenEvent.Init.Post#addListener` 与
     `ModContainer#registerExtensionPoint(IConfigScreenFactory.class, …)`

   > 26.3 把 GUI 从 `GuiGraphics`/`render(...)` 改成了 `GuiGraphicsExtractor`/`extractRenderState(...)`，
   > 原版也没有提供下拉框/滚动列表这类控件，所以 `DeviceList` 是按新 API 手写的控件。

4. **界面切换用 `Gui#setScreen`**
   本模组所有页面切换（入口按钮、`配置`/`返回`、`取消`/`确认`、重启提示）都走
   `minecraft.gui.setScreen(...)`，也就是原版每个界面用的那条路径，**不要**改成
   `Minecraft#setScreenAndShow(...)`：

   > `setScreenAndShow` 是原版卸载世界时用的（`clearClientLevel`、断线），它会紧接着调用
   > `renderFrame(false)` 立刻多渲染一帧（profiler 里的 `forcedTick`）。那一帧的
   > `GameRenderer` 把 `shouldRenderLevel` 算成 `false`（因为 `advanceGameTime=false`），于是
   > **有世界时那一帧不画世界**，而 `Screen#extractBackground` 仍然要画模糊背景和半透明的
   > `inworld_menu_background`，结果整帧全黑并直接呈现给玩家 —— 表现就是「切换界面时背景闪一下黑」
   > （主界面没有世界、画的是全景图，所以看不出来）。实测：同样一次切换，用 `setScreenAndShow`
   > 帧计数 +1 且截图 92% 像素为纯黑，用 `gui.setScreen` 帧计数 +0 且前后两帧逐像素完全相同。

---

## 项目结构

```
common/src/main/java/dev/vkselect/      # 与加载器无关的全部逻辑（会被两个模块一起编译）
  VkSelect.java                         # 常量与日志
  Env.java                              # 配置/游戏目录抽象（Fabric / NeoForge / 反射兜底）
  VkSelectConfig.java                   # config/vkselect.json 读写
  VkSelectRuntime.java                  # 当前设备、设备列表缓存、提示消息
  DeviceSelection.java                  # keyword → 设备的匹配规则
  VkSelectScreens.java                  # 入口按钮与 Overlay 的打开方式
  command/VkSelectCommandTree.java      # /vkselect 指令树（与 source 类型无关）
  gui/VulkanDeviceSelectScreen.java     # Overlay（设备选择页）
  gui/DeviceList.java                   # 滚动菜单控件（4 行 + 滚动条 + 拖拽）
  gui/VkSelectConfigScreen.java         # 配置页面（开关 + 返回）
  gui/RestartRequiredScreen.java        # 「重启 Minecraft?」提示（关闭游戏 / 稍后重启）
  gui/GuiText.java                      # 长文本的居中 / 滚动显示
  mixin/VulkanBackendMixin.java         # 接管原版设备选择
  vulkan/…                              # VkInstance 枚举与版本号解码
common/src/main/resources/
  vkselect.mixins.json                  # 两个加载器共用的 mixin 配置
  assets/vkselect/lang/{en_us,zh_cn}.json
fabric/                                 # Fabric 模块（entrypoint、ModMenu 集成、fabric.mod.json）
neoforge/                               # NeoForge 模块（@Mod、neoforge.mods.toml）
```

## 构建

```bash
./gradlew build                 # 两个加载器的 jar 都会输出到 */build/libs/
./gradlew :fabric:runClient     # 开发环境启动 Fabric 客户端
./gradlew :neoforge:runClient   # 开发环境启动 NeoForge 客户端
```

产物：

```
fabric/build/libs/vkselect-fabric-26.3-1.0.0.jar
neoforge/build/libs/vkselect-neoforge-26.3-1.0.0.jar
```

冒烟测试脚本（会启动客户端、等后端确定后自动关掉游戏，避免留下窗口）：

```bash
scripts/verify-dev-run.sh fabric      # 或 neoforge
```

> 首次构建需要联网下载 Minecraft 26.3、Fabric/NeoForge 的开发环境依赖。
> 若运行环境无法写入 `~/.gradle`（只读 HOME、沙箱等），可以用
> `GRADLE_USER_HOME=$PWD/.gradle-home ./gradlew build`，并把已有的
> `~/.gradle/caches` 通过 `GRADLE_RO_DEP_CACHE` 复用。

## 验证情况

本仓库在开发环境中做过的实测（Minecraft 26.3 + Fabric Loader 0.19.5 / Fabric API 0.161.0+26.3 + ModMenu 21.0.0，
以及 NeoForge 26.3.0.48-beta）：

| 项目 | 结果 |
| --- | --- |
| 两个加载器的 `./gradlew build` | 通过，产物为 `vkselect-fabric-26.3-1.0.0.jar` / `vkselect-neoforge-26.3-1.0.0.jar` |
| Fabric 开发客户端启动 | 模组被加载，`/vkselect` 与界面入口注册成功，日志无错误 |
| NeoForge 开发客户端启动 | 同上（`@Mod` 构造、`IConfigScreenFactory` 注册、事件监听均无异常） |
| Mixin 生效 | `VulkanBackendMixin` 成功注入 `VulkanBackend.findPhysicalDevice`，并在渲染后端创建时输出所选设备 |
| 选择真的生效 | 用 `--graphicsBackend vulkan` 强制 Vulkan 启动时，日志为 `Using graphics backend Vulkan`、`Using graphics device: llvmpipe (…)`，与实际配置的设备一致（同一次运行里读取实时后端也确认是 Vulkan） |
| Overlay 交互 | 用脚本模拟真实左键点击（SDL 键位：左键 = 1）逐个点击下拉条目与 Confirm，条目选中项、配置文件写入、"取消/确认"关闭行为都正确 |
| 长设备名显示 | 用真实机型的长设备名截图验证：超长文本在框内滚动、短文本居中，文字不越出面板 |
| 滚动菜单 | 截图验证 4 行可视 + 滚动条；脚本注入滚轮事件后行内容与滑块位置同步变化 |
| 配置页面 | 截图验证标题/两行开关/返回的布局；脚本点击开关后日志为 `toggle before = true → after = false`，且 `config.showInMainMenu` 立即变为 `false`；点击 `返回` 后回到设备选择页面 |
| 重启提示 | 脚本完整跑通「选中另一块 GPU → `确认` → 弹出 `RestartRequiredScreen` → 截图」；点 `Confirm` 后日志出现 `Stopping!` 且进程以 0 退出（原版退出路径），点 `I'll restart later` 则回到 `TitleScreen`；选当前这块 GPU 时不弹提示 |
| 界面切换不闪黑 | 探测脚本先建一个测试世界（有 `level` 才会暴露问题），再走真实路径依次切换 Pause → Overlay → 配置页 → Overlay → Confirm → 重启提示 → 稍后重启：每次切换帧计数 +0，切换后那一帧与切换前逐像素完全相同（mean abs diff = 0.000）；对照组用 `setScreenAndShow` 时帧计数 +1 且该帧 92% 像素为纯黑 |
| 设备枚举 | 用真实 Vulkan loader 跑通，输出与 `vulkaninfo` 完全一致（例如 `llvmpipe (LLVM 22.1.8, 256 bits)`、驱动 `llvmpipe 26.2.3`、类型 CPU） |
| `/vkselect select` 与 Overlay | 在运行中的客户端里实际保存了配置并写入 `config/vkselect.json`；指令结果通过指令反馈出现在聊天栏，Overlay 路径不产生聊天系统消息（见下一条） |
| 聊天消息来源 | 反射读取 `ChatComponent` 的消息列表：点 Overlay 的 `确认` 后没有任何新增系统消息，同一探针注入的对照消息能被检出；指令路径的反馈代码未改动 |
| 关闭模组时的行为对照 | 配置 `enabled: false` 时注入不再介入，游戏行为与纯原版一致 |

> 说明：测试机在沙箱里只能看到软件 Vulkan 设备（lavapipe）。另外 26.3 的「默认」图形 API 会**先尝试
> OpenGL**，只有在设为「偏好 Vulkan」时才轮到 Vulkan，所以自动化脚本里用 `--graphicsBackend vulkan`
> 强制后端来验证设备选择本身。多 GPU 的最终体感建议在自己的机器上确认：
> `选项 > 显示设置 > 图形 API > 偏好 Vulkan`，重启后在 `F3` 里查看设备名与 `(dGPU)/(iGPU)` 标记。

## 已知限制

- 设备选择在**下次启动**时才生效（原版在创建渲染后端时就已确定设备，运行中无法切换）。
- 模组**不会自己重启游戏**：提示页的 `Confirm` 只会像原版那样关闭游戏，重新启动需要玩家在启动器里操作；
  这样也不会绕过启动器的 GPU/参数设置。
- 只在 Vulkan 后端下有意义；OpenGL 后端不使用 Vulkan 设备。
- `/vkselect list` 会临时创建一个 Vulkan 实例；在完全没有 Vulkan loader 的机器上会返回一条错误
  信息（界面用红色文字显示），而不是崩溃。
- 若所选设备被禁用、驱动更新后改名等导致匹配失败，模组会记录警告并回退到原版选择。
- 只有当 Minecraft 自己的 `checkDeviceSuitability` 认可该设备时才会接管选择；如果这套检查因为版本变动而无法调用，
  模组会放弃接管、直接用原版结果，因此一个坏配置最多是「不生效」。
- 需要临时排除模组的干扰时，可以用 `-Dvkselect.disabled=true` 启动，或把配置里的 `enabled` 设为 `false`。
- 配置里保存的是**设备名**而不是枚举序号：`/vkselect list` 会打印 index，但同型号的两块卡无法区分，
  这种情况请用设备名里可区分的部分（例如不同驱动槽位/PCI 信息）来匹配。
- 指令补全按「包含」匹配（输入 `radeon` 也能补全出 `AMD Radeon ...`）；若设备名被驱动更新改名，
  需要重新选择一次。
- 26.3 改用 SDL 之后鼠标按键编号是 SDL 风格（左键 = 1，`AbstractWidget#isValidClickButton` 也只认 1），
  自定义控件不要再用旧的 `button() == 0` 判断左键。
