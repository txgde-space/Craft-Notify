# Craft Notify · 夸父逐讯

> [!NOTE]
> 🤖 **AI 含量警告：本模组完全由 AI 编写。人类主要负责提供红石，以及不断点击“继续”。**

<p align="center">
  <img src="src/main/resources/craft_notify.png" alt="Craft Notify 图标" width="160">
</p>

<p align="center">
  <strong>把 Minecraft 的红石信号发送到现实世界。</strong>
</p>

<p align="center">
  <a href="README.md">English</a> · 简体中文
</p>

Craft Notify（夸父逐讯）是一个面向 **Minecraft 1.21.1 / NeoForge** 的通知模组。给铜制通信终端接入能源、连接三格高通信天线，再输入红石上升沿，即可通过 **PushPlus、NotifyX 或自定义 Webhook** 发送自定义消息。

所有 HTTP 请求均异步执行，不会阻塞 Minecraft 服务端主线程。

## 功能

- 红石上升沿触发通知，不会在持续通电时重复发送
- 可视化终端 GUI，支持设备名、通知通道、标题、正文和冷却时间
- 内置 PushPlus 与 NotifyX 支持
- 支持任意 HTTP Webhook、自定义方法、请求头、请求体和成功状态码范围
- 可选 HTTP 回调监听器，用于接收异步投递结果
- 支持服务器、设备、维度、坐标、红石强度和时间等消息变量
- 终端所有方向均提供标准 NeoForge FE 能力
- 独立 Craft Notify 创造模式物品栏，同时加入原版“红石方块”分类
- 支持迁移 Otherworld Calling 和 Redstone Messenger 的配置及注册项
- 密钥仅保存在服务端配置中，不写入方块 NBT，也不会同步到客户端

## 运行环境

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge 21.1.244 或兼容的更新版 21.1.x |
| Java | 21 |
| 安装位置 | 客户端与服务端 |

## 安装

1. 安装 Minecraft 1.21.1 和对应版本的 NeoForge。
2. 下载 `craft-notify-neoforge-1.21.1-<版本>.jar`。
3. 将 JAR 放入客户端和服务端的 `mods` 目录。
4. 完整重启游戏或服务器。
5. 首次启动后编辑：

```text
config/craft-notify-channels.properties
```

修改通知通道后，可以在不重启服务器的情况下重新加载：

```text
/notify reload
```

> [!WARNING]
> 通知凭据属于服务端秘密。不要把真实密钥发给玩家、写进终端消息或提交到代码仓库；推荐通过环境变量读取。

## 方块

### 夸父通信终端

铜制外壳通信终端，正面具有显示屏、红石输入指示和独立状态灯。

- 能量容量：`10,000 FE`
- 每次被通知服务接受的发送消耗：`1,000 FE`
- 最大能量接收速度：`2,000 FE`
- 所有方向均暴露标准 `IEnergyStorage` 能力
- 右键打开配置 GUI
- 仅在红石从关闭变为开启时触发

### 夸父通信天线

将天线底座放在终端水平方向相邻的位置。放置时会自动搭建成 **三格高** 的完整天线，因此底座上方必须保留两格空间。

只有当天线结构完整、能量充足且指定通道存在时，终端才会发送通知。

## 合成表

### 夸父通信终端

```text
铜锭  红石      铜锭
石英  侦测器    石英
铁锭  红石火把  铁锭
```

### 夸父通信天线

```text
空    紫水晶碎片  空
铜锭  红石火把    铜锭
铜锭  铁锭        铜锭
```

## 使用方法

1. 放置夸父通信终端。
2. 在终端旁放置夸父通信天线，并确认三格结构完整。
3. 使用兼容 FE 的线缆或发电设备为终端供能。
4. 右键终端并配置通知信息。
5. 输入一次红石上升沿。
6. 查看 GUI 状态、玩家提示或服务端日志确认发送结果。

GUI 支持：

- 设备名称
- 通知通道 ID
- 通知标题
- 多行自定义正文
- `5–86400` 秒冷却时间
- 测试发送按钮

## 消息变量

标题和正文支持以下变量：

| 变量 | 内容 |
| --- | --- |
| `{server}` | 服务器名称 |
| `{label}` | GUI 中配置的设备名称 |
| `{dimension}` | 当前维度 |
| `{x}` `{y}` `{z}` | 终端坐标 |
| `{power}` | 当前红石信号强度 |
| `{time}` | 触发时间 |
| `{suppressed}` | 冷却期间被抑制的触发次数 |

默认模板：

```text
标题：[{server}] {label}
正文：{label} triggered at {dimension} ({x}, {y}, {z}), power {power}.
```

## 通知通道配置

同一个配置文件可以包含多个通道。第一个点号前的内容就是终端 GUI 中填写的通道 ID，例如 `default`、`notifyx` 和 `webhook`。

### PushPlus

```properties
default.type=pushplus
default.token=env:PUSHPLUS_TOKEN
default.topic=
default.template=markdown
default.channel=wechat
```

也可以直接填写 Token，但不推荐：

```properties
default.token=YOUR_PUSHPLUS_TOKEN
```

### NotifyX

```properties
notifyx.type=notifyx
notifyx.key=env:NOTIFYX_KEY
notifyx.description=Craft Notify redstone notification
notifyx.team=
```

NotifyX 使用 `key` 字段。旧配置中的 `token` 仍可作为兼容写法，但会在服务端日志中显示迁移提醒。

Craft Notify 会根据当前接口实际接受的限制安全截断 NotifyX 标题和正文，避免超长消息直接导致 HTTP 400。

### 自定义 Webhook

```properties
webhook.type=webhook
webhook.url=https://example.com/minecraft/events
webhook.method=POST
webhook.content_type=application/json; charset=utf-8
webhook.header.Authorization=env:WEBHOOK_AUTHORIZATION
webhook.header.X-Server=Minecraft
webhook.body={"request_id":"{request_id_json}","title":"{title_json}","content":"{content_json}","created_at":"{created_at_json}","callback_url":"{callback_url_json}"}
webhook.success_status_min=200
webhook.success_status_max=299
```

支持的 HTTP 方法：`POST`、`PUT`、`PATCH`、`GET`、`DELETE`。

Webhook 模板支持 `{request_id}`、`{title}`、`{content}`、`{created_at}`、`{channel}`、`{callback_url}` 和 `{callback_token}`。在 JSON 请求体中应使用对应的安全变量，例如 `{title_json}`、`{content_json}` 和 `{callback_url_json}`。

### Webhook 回调监听器

如果远端服务需要异步回传最终处理结果，可以启用内置回调监听器：

```properties
webhook.callback.enabled=true
webhook.callback.bind=127.0.0.1
webhook.callback.port=8765
webhook.callback.path=/craft-notify/callback
webhook.callback.public_url=https://example.com/craft-notify/callback
webhook.callback.token=env:WEBHOOK_CALLBACK_TOKEN
```

远端服务需要发送包含原始请求 ID 的 `POST`：

```http
Authorization: Bearer <WEBHOOK_CALLBACK_TOKEN>
Content-Type: application/json

{"request_id":"<request_id>","status":"delivered"}
```

安全默认值只监听 `127.0.0.1`。如需通过公网访问，建议使用 HTTPS 反向代理，不要直接暴露监听端口，并务必设置高强度回调 Token。

## 命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/notify channels` | 所有人 | 查看服务端已加载的通道 ID |
| `/notify reload` | OP 2 | 重新加载通道配置和回调监听器 |
| `/notify configure ...` | OP 2 | 不使用 GUI，直接配置指定坐标的终端 |

示例：

```text
/notify configure 100 64 -20 default "Iron Farm" "[{server}] {label}" "Storage is full at {dimension} ({x}, {y}, {z})" 30
```

## 能源模组兼容

终端使用 NeoForge 标准 FE 能力，因此可以直接连接 Mekanism、Thermal、Powah、Ender IO 等模组中兼容 FE 的线缆和设备。

- **Applied Energistics 2** 使用 AE 网络能源，需要其常规能源接收或转换路径。
- **Refined Storage** 遵循自身的网络与控制器供能规则，可以使用兼容的 FE 供电链路。
- **Create** 使用旋转应力，需要发电附属模组或其他 FE 转换设备。

Craft Notify 不强制依赖以上任何模组。

## 升级与迁移

`0.5.0` 起正式使用：

- 名称：`Craft Notify / 夸父逐讯`
- Mod ID：`craft_notify`
- Java 包：`dev.thou.craftnotify`
- 配置文件：`craft-notify-channels.properties`

如果新配置不存在，首次启动会复制找到的第一个旧配置：

1. `otherworld-calling-channels.properties`
2. `redstone-messenger-secrets.properties`

旧文件会保留为备份。NeoForge 注册表别名会迁移旧命名空间下的终端、天线、物品和终端方块实体。

> [!IMPORTANT]
> 升级前请备份重要世界。迁移后的世界不要再使用旧版模组打开，并且不要同时安装新旧两个 JAR。

## 从源码构建

需要 Java 21：

```bash
./gradlew clean build
```

构建产物位于：

```text
build/libs/craft-notify-neoforge-1.21.1-<版本>.jar
```

开发环境启动命令：

```bash
./gradlew runClient
./gradlew runServer
```

## 问题排查

如果通知发送失败：

1. 执行 `/notify channels`，确认 GUI 中填写的通道已经加载。
2. 执行 `/notify reload`，检查服务端日志中的配置错误。
3. 确认天线结构完整，且终端至少存有 `1,000 FE`。
4. 确认输入的是红石上升沿，而不是一直保持通电。
5. 检查 `logs/latest.log` 中的 HTTP 状态码与接口返回信息。
6. 确认 Minecraft 实际加载的是最新 JAR；模组升级必须完整重启。

## 许可证

MIT
