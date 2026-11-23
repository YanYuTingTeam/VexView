# VexView 2.7.3-1.8.9 — Release Notes

Minecraft 1.8.9 / Forge 客户端移植版。
服务端继续使用官方 **VexView 2.7.3 Spigot 插件**，协议、配置结构、贴图目录与本地化 key 均未改动。

## 安装

1. Minecraft 1.8.9 + Forge 11.15.1.1902
2. 把 `vexview-2.7.3-1.8.9.jar` 放进 `.minecraft/mods/`

## 这份构建

- 168 个 class，0 编译错误，MCP→SRG 重写 2 205 处成员引用
- 实机验证（真实 1.8.9 客户端）：FML 正常加载；自定义 GUI / GameMenu 公告面板 / 自定义药水 / 公告
  全链路断言通过；0 崩溃
- 构建方式：ForgeGradle 2.1（`build.gradle`），或离线流水线（`build-local.ps1`），两者产出等价

## 本版修复

- 空背包格导致的 GUI 崩溃（1.8.9 空槽为 `null`，`ConcurrentHashMap` 拒收）
- 事件监听器重复注册（1.8.9 的 `FMLCommonHandler#bus()` 与 `MinecraftForge.EVENT_BUS` 是同一条）
- 后台线程直接修改正在显示的界面（改为回到客户端线程）
- 语言文件命名（1.8.9 需 `en_US.lang` / `zh_CN.lang`，小写是 1.11+ 的规则）
- 反混淆遗留的调用点密钥解密（导致 GameMenu 右侧公告面板空白、`[local]` 本地贴图被当成 URL）

详细的逐项验证记录见 [`VERIFICATION-1.8.9.md`](VERIFICATION-1.8.9.md)。

## 校验值

```
vexview-2.7.3-1.8.9.jar
大小   : 856 811 bytes
SHA256 : 04daae9ca9828d84237615cf1bf3e820d9bc2e1c8c6f1d6810503fad592cba73
```
