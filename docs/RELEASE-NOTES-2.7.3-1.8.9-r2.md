# VexView 2.7.3-1.8.9-r2 — Release Notes

Minecraft 1.8.9 / Forge 客户端移植版（第一个包含社区修复的构建）。
服务端继续使用官方 **VexView 2.7.3 Spigot 插件**，协议、配置结构、贴图目录与本地化 key 均未改动。

## 安装

1. Minecraft 1.8.9 + Forge 11.15.1.1902
2. 把 `vexview-2.7.3-1.8.9-r2.jar` 放进 `.minecraft/mods/`（与 r1 二选一，不要同时放）

## 相对 r1 的改动

| 提交 | 修复内容 |
| --- | --- |
| `a0e9c87` | 左键取物时空槽位判空（此前空槽左键会 NPE） |
| `3735860` | 自定义药水槽位耗尽后的处理：药水池只注册一次，池满时沿用既有 id |
| `f9d9a9c` | 闪电特效改用 1.8.9 真实存在的音效 id（`ambient.weather.thunder` / `random.explode`） |
| `b1fdbdd` | 切换世界时清理 WorldTag，同一世界的更新保留既有 tag |
| `7efbb55` | GUI 请求统一回到客户端线程应用，丢弃过期构建，界面切换后再回发打开包 |
| `c2999da` | 等待 GUI 贴图在主线程注册完成；加载失败时显示既有下载错误；30 秒超时兜底 |

## 这份构建

- 147 个源文件 → **169 个 class**，0 编译错误，MCP→SRG 重写 2 198 处成员引用
- 构建路径：ForgeGradle 2.1（`build.gradle`）与离线流水线（`build-local.ps1`）均通过
- 实机验证（真实 1.8.9 客户端 + 探针投递真实格式数据包）：
  `gamemenu` 解析 3 行公告、`potion` 生成 `CustomPotion`、`VexGameMenu` 与含背包段的 `VexCustomGui` 正常打开、
  `effects/lightning` 无异常、`partialTicks` 连续插值、事件总线 14 个 VexView 监听器（无重复注册）、**0 崩溃**

## 校验值

```
vexview-2.7.3-1.8.9-r2.jar
大小   : 859 795 bytes
SHA256 : 59386fae6cd15537707cfb5c3abb8e61e72b7267e8ff5fac76a6ad060b3d1473
```
