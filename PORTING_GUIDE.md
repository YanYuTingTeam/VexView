# VexView 2.7.3 — port 1.12.2 → 1.8.9 : porting guide

> 公开仓库说明：这份文档最初是本仓库移植时使用的“施工规范”，现在作为 **1.12.2 → 1.8.9 的 API 对照表**保留。
> 里面的 URL / 命令 / 工具路径是移植当时的原样记录，按需替换即可；实战结论以 [README](README.md) 和
> [docs/VERIFICATION-1.8.9.md](docs/VERIFICATION-1.8.9.md) 为准。

This file is the contract for every agent working on this port. Read it fully before editing.

## Goal

`D:\src\vexview1.8.9\src\main\java` currently holds the **deobfuscated VexView 2.7.3 source written for
Minecraft 1.12.2 / Forge 14.23.5**. It must be turned into a source tree that compiles against
**Minecraft 1.8.9 / Forge 11.15.1** with **zero errors**, while behaving exactly like the 1.12.2 build:
same strings, same network packets, same GUI layout, same render order, same config keys.

## Hard rules

1. **No stubs, no no-ops, no "TODO"**. If a 1.12.2 API has no 1.8.9 counterpart, implement the same
   behaviour with the closest 1.8.9 API, or (only when the feature genuinely cannot exist on 1.8.9,
   e.g. an entity class that was added in 1.9) drop just that value and make sure the caller still
   handles `null` exactly as before.
2. **Never change string literals, packet channel names, JSON keys, config keys, or the
   `f1..fN` / `m1..mN` / `s1..sN` member names.** Servers speak to the client through those names.
3. **Minimal diffs.** The source is decompiler output (`var1`, `var2`, string-switch blocks ...).
   Keep the existing formatting and local variable names; change only what does not compile.
4. **Only edit the files assigned to you.** Shared helpers (`lk/vexview/compat/*`), build scripts and
   the SDK are off limits — if you need a change there, say so in your report instead of editing.
5. **Compile before you report.** Iterate until *your* files produce no errors.

## Toolchain (already built, use it, do not rebuild)

| What | Path |
| --- | --- |
| JDK 8 | `D:\deobf\_tools\jdk8\zulu8.78.0.19-ca-jdk8.0.412-win_x64` |
| Compile SDK (1.8.9 + Forge, MCP names) | `D:\src\vexview1.8.9\build-tools\minecraft-1.8.9-forge-mcp.jar` |
| Compile classpath (jar list, one path per line) | `D:\src\vexview1.8.9\build-tools\cp.txt` |
| Source file list (one path per line) | `D:\src\vexview1.8.9\build-tools\sources.txt` |
| Reference API dump (optional) | `D:\src\vexview1.8.9\build-tools\api189.txt` |

Look up any 1.8.9 signature with:

```
& 'D:\deobf\_tools\jdk8\zulu8.78.0.19-ca-jdk8.0.412-win_x64\bin\javap.exe' -p -cp D:\src\vexview1.8.9\build-tools\minecraft-1.8.9-forge-mcp.jar net.minecraft.client.gui.GuiScreen
```

Compile (write output to your own temp dir so parallel agents do not collide):

```powershell
$w='D:\src\vexview1.8.9'; $jdk='D:\deobf\_tools\jdk8\zulu8.78.0.19-ca-jdk8.0.412-win_x64'
$cp = (Get-Content "$w\build-tools\cp.txt") -join ';'
& "$jdk\bin\javac.exe" -source 8 -target 8 -encoding UTF-8 -nowarn -Xmaxerrs 20000 `
  -d "$env:TEMP\port-<yourname>" -cp $cp "@$w\build-tools\sources.txt" 2>&1 |
  Set-Content "$env:TEMP\port-<yourname>.txt" -Encoding UTF8
Select-String -Path "$env:TEMP\port-<yourname>.txt" -Pattern '<YourFile>\.java'
```

Other agents are editing other files in the same tree, so ignore errors outside your assignment.

## Mapping table already established (1.12.2 → 1.8.9)

Types / packages

| 1.12.2 | 1.8.9 |
| --- | --- |
| `net.minecraft.util.text.ITextComponent` | `net.minecraft.util.IChatComponent` |
| `TextComponentString` | `ChatComponentText` |
| `TextComponentTranslation` | `ChatComponentTranslation` |
| `TextFormatting` | `EnumChatFormatting` |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.util.BlockPos` |
| `net.minecraft.util.math.MathHelper` | `net.minecraft.util.MathHelper` |
| `net.minecraft.client.renderer.BufferBuilder` | `net.minecraft.client.renderer.WorldRenderer` |
| `net.minecraft.client.renderer.entity.RenderLivingBase` | `...entity.RendererLivingEntity` |
| `net.minecraft.client.renderer.RenderItem` | `net.minecraft.client.renderer.entity.RenderItem` |
| `net.minecraft.client.renderer.entity.RenderManager#renderEntity` | `#doRenderEntity` |
| `net.minecraft.init.SoundEvents` / `net.minecraft.util.SoundEvent` | **absent** — use `PositionedSoundRecord` (below) |
| `CPacketPing` / `CPacketServerQuery` / `SPacketPong` / `SPacketServerInfo` | `C01PacketPing` / `C00PacketServerQuery` / `S01PacketPong` / `S00PacketServerInfo` |
| `net.minecraft.entity.item.EntityMinecartCommandBlock` | `net.minecraft.entity.EntityMinecartCommandBlock` |
| `net.minecraft.entity.item.EntityMinecartMobSpawner` | `net.minecraft.entity.ai.EntityMinecartMobSpawner` |
| `net.minecraft.entity.item.EntityMinecart` (abstract) | `EntityMinecartEmpty` / subclasses |

Members

| 1.12.2 | 1.8.9 |
| --- | --- |
| `Minecraft#player` / `#world` / `#fontRenderer` | `#thePlayer` / `#theWorld` / `#fontRendererObj` |
| `Minecraft#getRenderPartialTicks()` | `Minecraft#timer` is private → `lk.vexview.compat.VexReflect.partialTicks()` |
| `GuiButton#x` `#y` / `GuiTextField#x` `#y` | `#xPosition` / `#yPosition` (still `width`, `height`) |
| `GuiButton#drawButton(Minecraft,int,int,float)` | `#drawButton(Minecraft,int,int)` (the 4-arg form is a different method on 1.8.9 — overriding it means the button is never drawn) |
| `GuiScreen#drawHoveringText` (public) | `protected` — call it from a `GuiScreen` subclass only |
| `GuiScreen#selectedButton` (public) | private → `VexReflect.setSelectedButton(screen, button)` |
| `ItemStack#getCount()` / `#setCount(n)` | `stackSize` field |
| `ItemStack.EMPTY` | `null` |
| `Item#getTranslationKey()` | `#getUnlocalizedName()` |
| `Item#getTooltip(EntityPlayer, ITooltipFlag)` | `#getTooltip(EntityPlayer, boolean advanced)` |
| `Slot#xPos` / `#yPos` | `#xDisplayPosition` / `#yDisplayPosition` |
| `PacketBuffer#readItemStack()` / `#writeItemStack(s)` | `#readItemStackFromBuffer()` / `#writeItemStackToBuffer(s)` |
| `Tessellator#getBuffer()` | `#getWorldRenderer()` |
| `RenderGameOverlayEvent.Pre#getType()`/`#getResolution()`/`#getPartialTicks()` | public fields `type` / `resolution` / `partialTicks` |
| `GuiScreenEvent.DrawScreenEvent#getMouseX/getMouseY/getRenderPartialTicks/getGui` | public fields `mouseX` / `mouseY` / `renderPartialTicks` / `gui` |
| `GuiOpenEvent#getGui()` / `#setGui(g)` | public field `gui` |
| `FMLNetworkEvent.ClientCustomPacketEvent#getPacket()` | `#packet()` |
| `ServerStatusResponse#getPlayers()` / `#getVersion()` | `#getPlayerCountData()` / `#getServerVersion()` |
| `GlStateManager.SourceFactor/DestFactor` enums | absent → `GL11.GL_SRC_ALPHA` etc. with `GlStateManager.tryBlendFuncSeparate(int,int,int,int)` |
| `PotionEffect#getPotion()` | `#getPotionID()` (int) → `Potion.potionTypes[id]` |
| `Potion(boolean,int)` | `Potion(int, ResourceLocation, int)` |
| `EntityMinecart#getType()` | `#getMinecartType()` returning `EntityMinecart.EnumMinecartType` |
| `KeyBinding#isActiveAndMatches(int)` | compare `#getKeyCode()` |

## 1.8.9 API notes that bite

* Sounds: `Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F))`
  (`net.minecraft.client.audio.PositionedSoundRecord`).
* Rendering with a `Tessellator`:
  `WorldRenderer r = Tessellator.getInstance().getWorldRenderer(); r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX); r.pos(x,y,z).tex(u,v).endVertex(); Tessellator.getInstance().draw();`
  (`WorldRenderer#pos` also exists in the 1.8.9 `WorldRenderer`; `color`, `norm`, `endVertex` are there too.)
* Chat components: `IChatComponent#getFormattedText()`, `#getUnformattedText()`, `#appendSibling(IChatComponent)`,
  `#appendText(String)`, `#setChatStyle(ChatStyle)`.
* `GuiScreen#drawScreen(int,int,float)` throws nothing; `mouseClicked`/`keyTyped` throw `IOException`.
* `GuiScreen#drawHoveringText(List<String>, int, int)` or `(List, int, int, FontRenderer)`.
* `Gui#drawTexturedModalRect(int,int,int,int,int,int)` and `drawModalRectWithCustomSizedTexture(int,int,float,float,int,int,int,int)`
  both exist.
* HUD element types are `RenderGameOverlayEvent.ElementType.{ALL,HELMET,PORTAL,CROSSHAIRS,BOSSHEALTH,ARMOR,HEALTH,FOOD,AIR,HOTBAR,EXPERIENCE,TEXT,HEALTHMOUNT}` —
  there is no `BOSSINFO` on 1.8.9.
* `ForgeHooks` exists (`net.minecraftforge.common.ForgeHooks`), `FMLClientHandler.instance().getClient()` exists.

## What to report back

* Files you touched and the error count you reached for them (target: 0).
* Every place where 1.8.9 forced a behaviour decision (e.g. "entity type absent → returns null"),
  so it can be listed in the final verification report.
