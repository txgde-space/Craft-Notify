# Craft Notify

> [!NOTE]
> 🤖 **AI inside:** This entire mod was written by AI. The human supplied the redstone and kept clicking **Continue**.

<p align="center">
  <img src="src/main/resources/craft_notify.png" alt="Craft Notify icon" width="160">
</p>

<p align="center">
  <strong>Turn Minecraft redstone signals into real-world notifications.</strong>
</p>

<p align="center">
  English · <a href="README_zh.md">简体中文</a>
</p>

Craft Notify is a notification mod for **Minecraft 1.21.1 and NeoForge**. Power a copper communication terminal, connect its three-block-tall antenna, and send a redstone rising edge to deliver a custom message through **PushPlus, NotifyX, or a generic Webhook**.

All HTTP requests run asynchronously and do not block the Minecraft server tick thread.

## Features

- Sends once per redstone rising edge instead of repeating while powered
- In-game terminal GUI for device name, channel, title, body, and cooldown
- Built-in PushPlus and NotifyX providers
- Generic HTTP Webhooks with custom methods, headers, bodies, and success ranges
- Optional HTTP callback listener for asynchronous delivery results
- Message variables for server, device, dimension, coordinates, power, and time
- Standard NeoForge FE energy capability on every terminal side
- Dedicated Craft Notify creative tab and entries in the vanilla Redstone Blocks tab
- Migration support for Otherworld Calling and Redstone Messenger configurations and registry IDs
- Server-only secrets that are never stored in block NBT or synchronized to clients

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.244 or a compatible newer 21.1.x build |
| Java | 21 |
| Installation side | Client and server |

## Installation

1. Install Minecraft 1.21.1 with NeoForge.
2. Download `craft-notify-neoforge-1.21.1-<version>.jar`.
3. Place the JAR in the `mods` directory on both the client and server.
4. Fully restart the game or server.
5. After the first launch, edit:

```text
config/craft-notify-channels.properties
```

Reload notification profiles and GUI dropdown presets without restarting the server:

```text
/notify reload
```

Edit dropdown options (device names, titles, bodies, cooldowns) in:

```text
config/craft-notify-presets.json
```

> [!WARNING]
> Notification credentials are server secrets. Never send them to players, store them in terminal messages, or commit them to a repository. Environment variables are recommended.

## Blocks

### Craft Notify Terminal

A copper communication terminal with a front display, a redstone input indicator, and an independent status light.

- Energy capacity: `10,000 FE`
- Cost per notification accepted by a provider: `1,000 FE`
- Maximum receive rate: `2,000 FE`
- Standard `IEnergyStorage` capability on every side
- Right-click configuration GUI
- Triggers only when redstone changes from off to on

### Craft Notify Antenna

Place the antenna base horizontally adjacent to the terminal. It automatically creates a **three-block-tall** antenna, so the two blocks above the base must be clear.

The terminal sends only when the antenna is complete, enough energy is available, and the selected channel exists.

## Recipes

### Craft Notify Terminal

Copper chassis, ender-range signalling, and an observer that watches the redstone edge:

```text
Copper Ingot   Lightning Rod    Copper Ingot
Ender Pearl    Observer         Ender Pearl
Copper Ingot   Copper Block     Copper Ingot
```

### Craft Notify Antenna

Amethyst crystal, a lightning-rod mast, and a copper base:

```text
Empty          Amethyst Shard   Empty
Copper Ingot   Lightning Rod    Copper Ingot
Copper Ingot   Copper Block     Copper Ingot
```

## Usage

1. Place a Craft Notify Terminal.
2. Place a Craft Notify Antenna next to it and make sure the full three-block structure is present.
3. Charge the terminal with an FE-compatible cable or power source.
4. Right-click the terminal and configure the notification.
5. Apply a redstone rising edge.
6. Check the GUI status, player message, or server log for the result.

The GUI provides:

- Device name
- Notification channel ID
- Notification title
- Multi-line custom body
- Cooldown from `5` to `86400` seconds
- Test notification button

## Message variables

Titles and bodies support the following variables:

| Variable | Value |
| --- | --- |
| `{server}` | Server name |
| `{label}` | Device name configured in the GUI |
| `{dimension}` | Current dimension |
| `{x}` `{y}` `{z}` | Terminal coordinates |
| `{power}` | Current redstone signal strength |
| `{time}` | Trigger time |
| `{suppressed}` | Triggers suppressed during cooldown |

Default templates:

```text
Title: [{server}] {label}
Body:  {label} triggered at {dimension} ({x}, {y}, {z}), power {power}.
```

## Notification profiles

The configuration file can contain multiple profiles. The text before the first dot is the channel ID entered in the terminal GUI, such as `default`, `notifyx`, or `webhook`.

### PushPlus

```properties
default.type=pushplus
default.token=env:PUSHPLUS_TOKEN
default.topic=
default.template=markdown
default.channel=wechat
```

A token can be written directly, but this is not recommended:

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

NotifyX profiles use `key`. The legacy `token` field remains available as a compatibility alias and produces a migration warning in the server log.

Craft Notify safely truncates NotifyX titles and bodies to the limits accepted by the current endpoint, preventing overlong messages from producing HTTP 400 responses.

### Generic Webhook

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

Supported HTTP methods are `POST`, `PUT`, `PATCH`, `GET`, and `DELETE`.

Webhook templates support `{request_id}`, `{title}`, `{content}`, `{created_at}`, `{channel}`, `{callback_url}`, and `{callback_token}`. Use their JSON-safe forms inside JSON bodies, such as `{title_json}`, `{content_json}`, and `{callback_url_json}`.

### Webhook callback listener

Enable the callback listener when a remote service needs to report its final delivery result asynchronously:

```properties
webhook.callback.enabled=true
webhook.callback.bind=127.0.0.1
webhook.callback.port=8765
webhook.callback.path=/craft-notify/callback
webhook.callback.public_url=https://example.com/craft-notify/callback
webhook.callback.token=env:WEBHOOK_CALLBACK_TOKEN
```

The remote service must send a `POST` containing the original request ID:

```http
Authorization: Bearer <WEBHOOK_CALLBACK_TOKEN>
Content-Type: application/json

{"request_id":"<request_id>","status":"delivered"}
```

The safe default binds only to `127.0.0.1`. For public access, use an HTTPS reverse proxy instead of directly exposing the listener, and always configure a strong callback token.

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/notify channels` | Everyone | List loaded channel IDs |
| `/notify reload` | OP level 2 | Reload profiles, GUI presets, and callback listeners |
| `/notify configure ...` | OP level 2 | Configure a terminal at a position without using the GUI |

Example:

```text
/notify configure 100 64 -20 default "Iron Farm" "[{server}] {label}" "Storage is full at {dimension} ({x}, {y}, {z})" 30
```

## Energy mod compatibility

The terminal exposes NeoForge's standard FE energy capability, so FE-compatible cables and devices from mods such as Mekanism, Thermal, Powah, and Ender IO can connect directly.

- **Applied Energistics 2** uses AE network power and needs its normal energy-acceptance or conversion path.
- **Refined Storage** follows its own network and controller power rules but can use a compatible FE supply chain.
- **Create** uses rotational stress and needs an addon or another FE conversion device.

Craft Notify has no hard dependency on these mods.

## Upgrading and migration

Version `0.5.0` adopts the release identity:

- Name: `Craft Notify / 夸父逐讯`
- Mod ID: `craft_notify`
- Java package: `dev.thou.craftnotify`
- Configuration: `craft-notify-channels.properties`

If the new configuration does not exist, the first launch copies the first available legacy file:

1. `otherworld-calling-channels.properties`
2. `redstone-messenger-secrets.properties`

The source file remains untouched as a backup. NeoForge registry aliases migrate terminals, antennas, their items, and terminal block entities from the old namespaces.

> [!IMPORTANT]
> Back up important worlds before upgrading. Do not open a migrated world with an older mod build, and never install both the old and new JARs at the same time.

## Building from source

Java 21 is required.

```bash
./gradlew clean build
```

The resulting JAR is written to:

```text
build/libs/craft-notify-neoforge-1.21.1-<version>.jar
```

Development runs:

```bash
./gradlew runClient
./gradlew runServer
```

## Troubleshooting

If a notification fails:

1. Run `/notify channels` and verify that the GUI channel ID is loaded.
2. Run `/notify reload` and inspect the server log for configuration errors.
3. Confirm that the antenna is complete and the terminal contains at least `1,000 FE`.
4. Confirm that the input is a rising edge rather than continuously powered redstone.
5. Check `logs/latest.log` for the HTTP status and provider response.
6. Verify that Minecraft loaded the latest JAR. Mod updates require a full restart.

## License

MIT
