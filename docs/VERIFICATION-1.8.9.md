# VexView 2.7.3 — Minecraft 1.8.9 移植验证报告

被测对象：本仓库源码 → `build/libs/vexview-2.7.3-1.8.9.jar`
基线对象：`D:\deobf\vexview\VexView-2.7.3-deobf`（MC 1.12.2 / Forge 14.23.5 的可编译反混淆版本）
运行环境：Minecraft 1.8.9 + Forge 11.15.1.1902 + JDK 8（zulu 8.0.412）

---

## 1. 静态验证

| 检查 | 结果 |
| --- | --- |
| `javac 1.8` 全量编译 | **0 error**（146 个源文件 → **168 个 class**，与 1.12.2 基线类数完全一致） |
| MCP 成员名 → SRG reobf | 13 597 处成员引用，**2 205 处改写**，**0 处改写失败** |
| reobf 后未映射的 `net/minecraft/**` 引用 | **3 处**，逐一确认无需映射（见下） |
| 资源 | `assets/vexview/**`、`mcmod.info`、`pack.mcmeta` 全部打包；`mcversion` 改 1.8.9，`pack_format` 改 1 |

reobf 报出的 3 处“未映射”都**不是**缺陷：

* `net/minecraft/inventory/Slot#getSlotIndex()` —— Forge binpatch 给 1.8.9 加的访问器，运行期本就是这个名字；
* `net/minecraft/potion/Potion#renderInventoryEffect(IILnet/minecraft/potion/PotionEffect;Lnet/minecraft/client/Minecraft;)V` —— 同样是 Forge 在 1.8.9 给 `Potion` 加的方法；
* `net/minecraft/util/IChatComponent#iterator()` —— 来自 `Iterable` 接口，本来就没有 SRG 名。

## 2. 实机验证（真实 1.8.9 客户端）

构建方式：`runtest.ps1` 用 JDK 8 直接拉起 `net.minecraft.launchwrapper.Launch` + `FMLTweaker`，
游戏目录 `runtest/`，mods 目录内放被测 jar 和一个**不随包发布**的验证探针 `vexprobe.jar`。

探针把“服务端 → 客户端”的 VexView 数据包（`gzip(JSON)`）真实投递到 `MinecraftForge.EVENT_BUS` 上的
`FMLNetworkEvent.ClientCustomPacketEvent`，因此走的是**完整链路**：
`压缩包 → 频道事件 → VexView.onClientPacket → NetPacketManager.m5 → JSON 分发 → 业务对象 → 渲染`。

| 验证项 | 观测结果 |
| --- | --- |
| FML 载入 | `5 mods loaded`（Minecraft/Forge/FML/FMLCore-access/VexView + 探针），`FMLFileResourcePack:VexView` 生效 |
| `@Mod.EventHandler preload` | 生成 `config/vexview.cfg`，`autoconnect / client / connect / connectgui / imagepath / relativelayout / update` 全部段落与 1.12.2 一致 |
| `@Mod.EventHandler init` | `ClientRegistry.registerKeyBinding` 完成，无异常 |
| 4 条频道注册 | `vexview / Base64VexView / HudBaseVexView / ChatVexView` 均建立 |
| `announce` 数据包 | 受理；`AnnGui.s2` 条数随包增长，帧计数推进（`tick 340: 2 条, f3=916/392`；`tick 700: 1 条, f3=400`，前一条按 `m3()` 到期被移除）——即 HUD 渲染循环确实在执行 |
| `potion` 数据包 | `activePotionEffects=1`，`id=85`，`Potion.potionTypes[85].getClass()=lk.vexview.potion.CustomPotion`，`duration=5977`，`amplifier=1`，`bad=false` |
| `gui` 数据包 | `Minecraft.currentScreen = lk.vexview.gui.VexCustomGui`；截图可见自定义背景贴图、`[txt]` 文本元素与 `[but]` 按钮元素；ESC 可关闭（`closable=true`） |
| 崩溃/异常 | 全流程 **0 崩溃、0 未捕获异常** |

## 3. 实机测试中发现并修复的移植缺陷

1. **`ItemStack.EMPTY` 语义丢失（真实缺陷，会导致开 GUI 必崩）**
   1.12.2 源码用 `ItemStack.EMPTY` 作为“空物品”哨兵做比较，批量迁移把它替换成了 `null`，
   而 `ItemPickTools.m4(a, b)` 会直接解引用第二个参数 —— 打开任何带物品槽的 GUI 都会 NPE。
   已按 1.12.2 的语义显式实现：`m4(空, 空)=true`、`m4(有, 空)=m4(空, 有)=false`，
   其余分支保持原逻辑。修复后同一测试用例不再崩溃（见第 2 节）。
2. **`FMLNetworkEvent.CustomPacketEvent#getPacket()`** 在 1.8.9 是公有字段 `packet`（无 getter），已全部改为字段访问。
3. **`GuiTextField`/`GuiButton` 的 `x`/`y`** 在 1.8.9 叫 `xPosition`/`yPosition`；`drawButton` 只有 3 参版本
   （4 参版本是死代码，会导致自定义按钮的绘制被父类覆盖）——已统一改为 3 参。

## 4. 因 1.8.9 缺少对应 API 而做的行为等价替换

| 1.12.2 | 1.8.9 处理 | 影响 |
| --- | --- | --- |
| `Minecraft#getRenderPartialTicks()` | `VexReflect.partialTicks()`（经 `ObfuscationReflectionHelper` 读私有 `timer`，缓存字段） | 数值完全一致 |
| `GuiScreen#selectedButton`（public） | `VexReflect.setSelectedButton()` 反射写入 | 按下态照常显示 |
| `GuiScreen#drawHoveringText`（public） | 1.8.9 为 `protected`，改用 `GuiUtils.drawHoveringText(...)`（等价实现，GUI 自身宽高/字体） | 文本提示一致 |
| `RenderTooltipEvent`（Forge 1.11+） | 1.8.9 无此事件 → 在各 `GuiScreen` 的 `renderToolTip` 覆写里调用 `ToolTipRender.m7/m8` | `backimage` 自定义物品提示、旋转物品预览行为不变 |
| `KeyBinding#isActiveAndMatches(int)` | `gameSettings.keyBindInventory.getKeyCode() == key` | 等价 |
| `GuiScrollingList#handleMouseInput(int,int)` | 1.8.9 无此方法，滚动在 `drawScreen(int,int,float)` 内处理 | 滚轮滚动行为一致 |
| `SoundEvent` / `SoundEvents` | `PositionedSoundRecord.create(new ResourceLocation("gui.button.press" / "dig.stone" / "entity.lightning_bolt.*"), volume)` | 声音资源名与音量、音高一致 |
| `RenderGameOverlayEvent.ElementType.BOSSINFO` | 1.8.9 无此枚举值，仅保留 `BOSSHEALTH` | Boss 血条隐藏功能完整 |
| `Potion(boolean,int)` + `#setBeneficial()` | 1.8.9 无该构造器、且注册表在 preInit 后冻结 → 在 `VexView#preload` 里由 `PotionTools.m0()` 预注册 32+32 个槽位，运行期只改图标/名字 | 自定义药水显示效果不变；上限 64 个并存的“不同名”自定义药水 |
| `Potion#renderHUDEffect(...)` | 1.8.9 该方法不存在（只有 `renderInventoryEffect`） | 背包内药水效果条目的自定义图标/名称/计时照常 |
| `Slot#xPos/yPos`、`IInventory#isUsableByPlayer`、`ItemStack#getCount/setCount`、`PotionEffect#getPotion()` | `xDisplayPosition/yDisplayPosition`、`isUseableByPlayer`、`stackSize`、`getPotionID()`+`Potion.potionTypes` | 语义等价 |
| `BufferBuilder` / `Tessellator#getBuffer()` | `WorldRenderer` / `#getWorldRenderer()` | 顶点数据一致 |
| `net.minecraft.util.text.*` | `net.minecraft.util.IChatComponent` / `ChatComponentText` / `ChatComponentTranslation` / `EnumChatFormatting` | 文本组件行为一致 |
| `net.minecraft.util.math.*` | `net.minecraft.util.BlockPos` / `MathHelper`（`floor`→`floor_float`、`clamp`→`clamp_int`） | 数值一致 |
| `EntityMinecart.Type` 匿名子类 | `EntityMinecartEmpty`；`EntityMinecartCommandBlock`/`MobSpawner` 改到 1.8.9 所在包 | 实体渲染一致 |

### 实体表裁剪（`VexEntityGeter`）

1.9+ 才存在的实体在 1.8.9 上不存在，按“能对上就用最接近的原版实体，对不上就不渲染”的原则处理：

* 替代：`DONKEY/MULE/LLAMA/SKELETON_HORSE/ZOMBIE_HORSE → EntityHorse`、
  `ELDER_GUARDIAN → EntityGuardian`、`DRAGON_FIREBALL → EntityLargeFireball`、
  `SPECTRAL_ARROW → EntityArrow`、`HUSK/ZOMBIE_VILLAGER → EntityZombie`、
  `STRAY/WITHER_SKELETON → EntitySkeleton`
* 不渲染（`null`，调用方原本就有 null 判定）：`AREA_EFFECT_CLOUD`、`EVOKER`、`EVOKER_FANGS`、
  `ILLUSIONER`、`LLAMA_SPIT`、`PARROT`、`POLAR_BEAR`、`SHULKER`、`SHULKER_BULLET`、`VEX`、`VINDICATOR`

## 5. 复现方式

```powershell
# 1) 全量构建（编译 + MCP→SRG reobf + 打包）
pwsh -File .\build-local.ps1

# 2) 实机验证（需要本机已安装 1.8.9 与 Forge 11.15.1.1902）
pwsh -File .\probe\build-probe.ps1     # 构建验证探针（不随模组发布）
pwsh -File .\runtest.ps1 -TimeoutSeconds 420
# 探针会自动进入存档、投递 announce/potion/gui 数据包，断言写入 runtest\logs\stdout.txt
```

工具链（`build-tools/`）：

* `SdkBuilder.java` —— 用 vanilla 1.8.9 jar + Forge binpatch + `joined.srg` + mcp_stable-22 生成 MCP 名编译 SDK；
* `GenReobf189.java` —— 生成 `mcp2srg-1.8.9.map`；
* `Reobf.java` —— 继承关系感知的 MCP→SRG 重写器，并报告无法映射的成员。

---

## 6. 实机反馈问题的修复（第二轮）

在真实服务器上试玩后反馈了两个问题，均已定位根因、修复并复测。

### 6.1 按钮显示成未翻译的 key（`goto.server.website` / `gui.advancements` / `disconnect.server` …）

**根因**：语言文件命名。1.12.2 用 `lang/en_us.lang`（小写，pack_format 3），
而 **1.8.9 读的是 `lang/en_US.lang` / `zh_CN.lang`（地区码大写）**。
移植时沿用了 1.12.2 的小写文件名，1.8.9 根本不加载它们，`I18n.format(key)` 于是原样返回 key。

**修复**：`assets/vexview/lang/` 改为 `en_US.lang` + `zh_CN.lang`。

**复测**：游戏菜单按钮恢复为 `Server Website` / `Advancements` / `Disconnect`
（中文客户端为 `服务器官网` / `成就` / `离开服务器`）。截图见 `docs/evidence/game-menu-fixed.png`。

### 6.2 右侧公告面板（服务器更新日志）空白

**根因**：`VexGameMenu.m4` 用 `SBPacketBuffer.m1("\"^2L")` 解出报文分隔符。
这 13 个 `m1..m13` 是**“调用点密钥”加密**：密钥 = 调用方 `类名+方法名`（`new RuntimeException().getStackTrace()[1]`）。
反混淆改了类名/方法名 → 运行期解出的不再是最初的 `"<##>"`，于是
`string.split(<乱码>)[3]` 取不到第 4 段并抛异常，`s6`（公告行）始终为空 —— 面板只画出 `broad.png` 背景，没有文字。
（`s3`/`s4` 用的是字面量 `"<##>"`，所以网页按钮和标题是正常的，与你截图现象完全一致。）

**修复**：把 4 处 caller-keyed 解密调用替换成原文明文（明文来自原反混淆工程的解密表 `strings3.json`）：

| 位置 | 原调用 | 替换为 |
| --- | --- | --- |
| `VexGameMenu#m4` | `SBPacketBuffer.m1("\"^2L")` | `"<##>"` |
| `VexGui#<init>` | `VerNetManager.m2("kn_aQnm")` | `"[local]"` |
| `VexImage`（2 处） | `VerNetManager.m2("KN\u007fAqnm")` | `"[local]"` |
| `VexMcImage` | `ChatString.m1("eKqd_kc")` | `"[local]"` |

后三处是同一类缺陷：`[local]` 判定失效会让“本地贴图”被当成 URL 去下载，
现在本地 GUI 背景/图片/按钮贴图也能正常识别。

**复测**（探针投递真实格式的 `gamemenu` 包 `x<##>url<##>title<##>行1<#>行2`）：

```
[vexprobe] gamemenu after gamemenu packet: lines=3 [Update log line one, Update log line two, third line]
           url=https://www.vexview.top title=烟雨庭
[vexprobe] ASSERT menuScreen=lk.vexview.ui.menu.VexGameMenu
[vexprobe] effects after potion packet: count=1  id=85 potionClass=lk.vexview.potion.CustomPotion
[vexprobe] ASSERT currentScreen=lk.vexview.gui.VexCustomGui
```

同一轮里 announce / potion / gamemenu / gui 四条链路全部通过，0 崩溃。

> 备注：这两个问题同样存在于 1.12.2 的反混淆源码（都来自反混淆副作用），
> 若用该源码另行构建 1.12.2 版本，需要做同样的两处修正。

---

## 7. 第三方代码审查提出的问题的处理（第三轮）

收到一份基于字节码核对（未启动游戏）的审查意见，逐条核对后处理如下。

| 审查意见 | 结论 | 处理 |
| --- | --- | --- |
| 空背包格会导致 GUI 打不开：`VexGui.m17()` 把玩家 36 格复制进 `VexInventory`，1.8.9 的空槽是 `null`，`ConcurrentHashMap` 拒收 `null` → NPE | **成立**，且属移植引入：1.12.2 的空槽是 `ItemStack.EMPTY`（非 null） | `VexInventory` 改用 `Collections.synchronizedMap(HashMap)`，保留“存 null 即空槽”的原语义 |
| 14 个监听器各注册两份（`MinecraftForge.EVENT_BUS` 与 `FMLCommonHandler.bus()`） | **成立**：1.8.9 的 `FMLCommonHandler#bus()` 直接返回同一个 `MinecraftForge.EVENT_BUS`（已用 11.15.1.1902 字节码确认），而代码两处都 `new X()`，于是每类都多出一个实例 | 只保留一次注册；事件不再重复执行 |
| `VexGuiBuilder.m6` 在工作线程里直接改正在显示的界面 | **成立**：`VexView.m1` 是线程池，复用分支没有回到主线程 | 复用分支改为 `Minecraft#addScheduledTask`，解析仍在工作线程，只有界面变更回主线程 |
| `VexReflect` 用 MCP 字段名（`timer` / `selectedButton`）反射，在 SRG 运行环境会失败、`partialTicks` 恒为 0 | **不成立**：`ObfuscationReflectionHelper` 会先做名字映射，实机采样 `partialTicks` 为 0.122~0.154 的连续变化值 | 仍然做了加固：改为**按字段类型**查找（`Minecraft` 里唯一的 `Timer` 字段、`GuiScreen` 里唯一的 `GuiButton` 字段），不再依赖任何命名空间 |

复测（真实 1.8.9 客户端，带 `inventory` 段的 GUI）：

```
[vexprobe] gamemenu after gamemenu packet: lines=3 [Update log line one, ...] url=... title=烟雨庭
[vexprobe] effects after potion packet: count=1  id=85 potionClass=lk.vexview.potion.CustomPotion
[vexprobe] ASSERT menuScreen=lk.vexview.ui.menu.VexGameMenu
[vexprobe] ASSERT currentScreen=lk.vexview.gui.VexCustomGui     ← 含 36 格背包，空槽不再崩
[vexprobe] tick 400 partialTicks=0.13765478 vexviewHandlers=14   ← 14 个注册（修复前 28）
[vexprobe] tick 410 partialTicks=0.12296486                      ← 插值连续变化
```

两套构建（离线 `build-local.ps1` / ForgeGradle `build.gradle`）都编译通过，产出的 jar 都做过实机验证。
