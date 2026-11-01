<div align="center">
  <img src="./docs/images/logo.png" alt="AstrBot for Android" width="120" />
  <h1>AstrBot for Android · SnowLuma 版</h1>
  <p><strong>简体中文</strong> | <a href="./docs/README.en.md">English</a></p>
  <p><strong>把 AstrBot、SnowLuma 和 QQ，装进你的 Android 手机。</strong></p>
  <p>一个 APK · 本地运行 · 原生管理 · 内置桌面与 WebUI</p>
  <p>
    <img src="https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?style=for-the-badge&amp;logo=kotlin&amp;logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-Native_UI-4285F4?style=for-the-badge&amp;logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Ubuntu-PRoot-E95420?style=for-the-badge&amp;logo=ubuntu&amp;logoColor=white" alt="PRoot Ubuntu" />
  </p>
  <p>
    <a href="https://github.com/HZP-BT/astrbot-for-android-snowluma/releases/latest"><img src="https://img.shields.io/badge/Version-1.3.0s6-3867FF?style=flat-square" alt="Version 1.3.0s6" /></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8.0 and above" />
    <img src="https://img.shields.io/badge/Architecture-ARM64-64748B?style=flat-square" alt="ARM64" />
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-18A978?style=flat-square" alt="MIT License" /></a>
    <img src="https://img.shields.io/badge/SnowLuma-v2.2.1-0A7E8C?style=flat-square" alt="SnowLuma 2.2.1" />
    <a href="https://github.com/HZP-BT"><img src="https://img.shields.io/badge/维护者-HZP--BT-111827?style=flat-square&amp;logo=github" alt="HZP-BT" /></a>
  </p>
  <p>
    <a href="#功能特性">功能特性</a> ·
    <a href="#界面预览">界面预览</a> ·
    <a href="#项目架构">项目架构</a> ·
    <a href="#开始使用">开始使用</a> ·
    <a href="#snowluma-接入说明">SnowLuma 接入</a> ·
    <a href="#源码构建">源码构建</a> ·
    <a href="#常见问题">常见问题</a> ·
    <a href="./docs/使用指南.md">使用指南</a> ·
    <a href="./docs/CHANGELOG.md">更新记录</a>
  </p>
</div>

## 项目简介

**AstrBot for Android（SnowLuma 版）** 是一个在 Android 手机本地运行 AstrBot 与 SnowLuma 的一体化管理应用。它把 Ubuntu 运行环境、AstrBot、Linux QQ、Ubuntu 桌面和 SnowLuma 打包进一个 APK，通过原生管理界面完成环境初始化、服务启停、QQ 登录、模型接入和后台管理，**无需 Root**。

应用内置 PRoot Ubuntu 24.04 运行环境，AstrBot 与 SnowLuma 都以本地服务运行：AstrBot 提供模型、平台、插件与人设管理；SnowLuma 把已登录的 Linux QQ 会话转换为 OneBot v11 接口，通过反向 WebSocket 连接 AstrBot。QQ 登录在应用内置的 Ubuntu 桌面中扫码完成，全程不需要在电脑上配置。

当前发布版本 **1.3.0-snowluma-6**，固定使用 **SnowLuma v2.2.1**（本项目实测可用的版本），并针对 Android/PRoot 环境做了启动注入与进程管理适配。

## 功能特性

### 一体化本地部署

- **单 APK 内置环境**：Ubuntu 24.04.3、AstrBot 4.29.3、Linux QQ 3.2.34、Xvfb/Fluxbox/noVNC 桌面和运行依赖全部随包提供，离线初始化即可使用。
- **原生管理界面**：Kotlin + Jetpack Compose，首页直接管理 AstrBot、Ubuntu 桌面与 SnowLuma 三张服务卡，提供启停、重启、打开后台和详情入口。
- **内置后台**：AstrBot 管理后台（默认 `127.0.0.1:6185`）和 SnowLuma 管理后台（默认 `127.0.0.1:5099`）都在应用内打开，无需外部浏览器。

### SnowLuma 接入

- **官方包安装**：从 SnowLuma 官方 Release 获取 Linux ARM64 完整包，按 SHA-256 校验后解压，不修改程序文件。
- **启动注入**：通过 `guest-compat` 在原版 QQ 启动时加载 SnowLuma 原生 hook，等待 `wrapper.node` 就绪后初始化，QQ 登录仍然走官方客户端。
- **与 AstrBot 对接**：SnowLuma 以反向 WebSocket 客户端连接 AstrBot 的 OneBot v11 服务，默认使用 6199 端口，也支持自定义端口（两边保持一致即可）。
- **版本锁定**：固定 **v2.2.1**。v2.3.x 更换原生 hook 后，在 Linux QQ 3.2.34-53644 上注入会导致 QQ 进程退出；本项目以实测可用的 v2.2.1 为默认。
- **配置保留**：安装、更新或回退版本时会保留 SnowLuma 的 `config` 与 `data`，WebUI 密码和账号配置不会丢失。

### 稳定性与进程管理

- **孤儿进程回收**：服务启动前和停止后都会清理被系统强杀（退出码 137）后残留的进程，并等待端口释放，避免“端口被占用”导致反复启动失败。
- **进程监督**：每个服务独立监督，异常退出自动记录并提供恢复；前台服务持有唤醒锁，页面关闭不影响运行。
- **健康检查**：内置探测确认 AstrBot 和 SnowLuma 后台真正就绪后才显示运行状态。
- **日志与诊断**：按服务分类的滚动日志，自动脱敏密码、Token 和二维码，支持导出诊断包。

### 桌面、终端与扩展

- **Ubuntu 桌面**：Xvfb + Fluxbox + noVNC，内置浏览器即可操作，用于 QQ 扫码登录和运行其他 Linux 图形程序。
- **终端与 SSH**：内置 Ubuntu 终端与 SSH 服务，可在手机上执行命令、管理文件和远程接入。
- **可选扩展**：DeepSeek Harness、cpolar 内网穿透按需安装，不增加默认包体积。
- **设备访问**：可选的 Shizuku 后台子进程管理与 Android 设备访问能力，用于排查系统进程限制和自动化。

### 数据与安全

- **备份恢复**：支持本机快照和导出到应用外的便携备份；升级、回滚和恢复前自动创建快照。
- **凭据保护**：WebUI 登录信息、API Key、Token 等在日志中自动脱敏。
- **下载校验**：QQ、SnowLuma 和更新包均校验大小与 SHA-256，校验失败不会覆盖现有程序。

## 界面预览

<table width="100%" align="center">
  <tr>
    <td align="center" width="50%">
      <img src="./docs/images/personas.webp" alt="人设：社区市场、搜索与本地预设" width="240" />
      <p align="center"><strong>人设 · 搜索与切换</strong></p>
    </td>
    <td align="center" width="50%">
      <img src="./docs/images/websites.webp" alt="网页：带网站图标的常用地址与内置访问入口" width="240" />
      <p align="center"><strong>网页 · 常用地址</strong></p>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/images/settings.webp" alt="设置：运行与权限、数据与维护、通用分类" width="240" />
      <p align="center"><strong>设置 · 分类管理</strong></p>
    </td>
    <td align="center" width="50%">
      <img src="./docs/images/extensions.webp" alt="扩展：终端与桌面、远程连接和 AI 工作台" width="240" />
      <p align="center"><strong>扩展 · 按需安装</strong></p>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/images/permissions.webp" alt="权限：Shizuku 授权、后台子进程统计和息屏白名单" width="240" />
      <p align="center"><strong>权限 · 状态与统计</strong></p>
    </td>
    <td align="center" width="50%">
      <img src="./docs/images/astrbot-webui.webp" alt="应用内的 AstrBot 管理后台" width="240" />
      <p align="center"><strong>AstrBot · 内置后台</strong></p>
    </td>
  </tr>
</table>

## 项目架构

```
Android App（Kotlin / Jetpack Compose）
├─ RuntimeService（前台服务 · 进程监督 · 端口与日志）
├─ PRoot Ubuntu 24.04（内置运行镜像）
│  ├─ AstrBot 4.29.3 ── WebUI 6185
│  ├─ Linux QQ 3.2.34 ── SnowLuma 原生 hook
│  ├─ SnowLuma 2.2.1 ── WebUI 5099 ── OneBot v11 反向 WS → AstrBot
│  ├─ Xvfb / Fluxbox / noVNC ── 内置桌面
│  ├─ 终端 / SSH / DeepSeek Harness
│  └─ /persistent 用户数据与备份
└─ WebView ── 内置后台与网页
```

消息链路：

```
QQ 客户端（手机内核上的 Linux QQ）
        │  SnowLuma 注入
        ▼
SnowLuma 运行时 ── OneBot v11 反向 WebSocket ──► AstrBot ──► 大模型
        │
        └─ WebUI 5099（账号、连接、日志）
```

## 开始使用

### 环境要求

- Android 8.0（API 26）及以上，**ARM64** 手机，无需 Root。
- 建议至少预留 3 GB 可用空间（首次解包运行环境约 2.1 GB）。
- 首次初始化环境、安装桌面依赖和下载 QQ 时需要联网；AstrBot 与运行环境随 APK 提供，不依赖外部下载。

### 安装步骤

1. 从 [Releases](https://github.com/HZP-BT/astrbot-for-android-snowluma/releases/latest) 下载 ARM64 安装包并安装。
2. 打开应用完成 Ubuntu 运行环境初始化，等待解包完成。
3. 在首页点击 **启动全部**，启动 AstrBot；进入 AstrBot 后台完成初始密码设置、模型提供商和默认模型配置。
4. 在首页 **Ubuntu 桌面** 卡片点详情，安装桌面依赖和原版 QQ；然后点 **启动桌面** 并在内置桌面中扫码登录 QQ。
5. 在首页 **SnowLuma** 卡片点详情，阅读并确认官方许可后安装 SnowLuma（固定 v2.2.1），再回到卡片点 **启动 SnowLuma**。
6. 打开 SnowLuma 后台（`127.0.0.1:5099`），用启动日志中的初始密码登录并修改密码，为账号添加 OneBot 反向 WebSocket 客户端（见下节）。
7. 用另一个 QQ 账号私聊机器人账号，确认消息往返。

> 便携备份不包含 QQ 会话目录；卸载应用或清除数据会删除本机快照，重要数据请先导出到应用外。QQ 登录在桌面中完成，更换设备或重装后需要重新扫码。

## SnowLuma 接入说明

### OneBot 连接配置

在 SnowLuma 后台进入账号的 **OneBot 协议端点**，添加 **WS 客户端（反向连接）**：

| 项目 | 填写内容 |
| --- | --- |
| 类型 | WebSocket 客户端 / WS Client（反向连接） |
| 地址 | `ws://127.0.0.1:6199/ws`（与 AstrBot 的 OneBot 端口一致） |
| Token | 与 AstrBot 的 OneBot 连接相同，默认留空 |
| 角色 | `Universal` |
| 消息格式 | `array` |
| 启用 | 开 |

AstrBot 侧只保留一条启用的 OneBot v11 连接：反向 WebSocket 主机 `127.0.0.1`、端口 `6199`（或自定义且两边一致）、Token 留空。保存后 SnowLuma 的客户端应显示“已连接”，AstrBot 的连接状态也会变为已连接。

### WebUI 密码

SnowLuma 的登录凭据保存在程序目录的 `config/webui.json`，应用日志会脱敏密码。忘记密码或更新后密码失效时，停止 SnowLuma，在 Ubuntu 终端执行：

```bash
cd /manager/extensions/snowluma/program && rm -f config/webui.json
```

```bash
HOME=/persistent/snowluma/home XDG_CONFIG_HOME=/persistent/snowluma/home/.config XDG_DATA_HOME=/persistent/snowluma/home/.local/share XDG_CACHE_HOME=/persistent/snowluma/home/.cache SNOWLUMA_HOOK_AUTOLOAD=0 SNOWLUMA_WEBUI_HOST=127.0.0.1 SNOWLUMA_WEBUI_PORT=5099 SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD='Snowluma@2026' /usr/bin/python3 /manager/extensions/desktop/guest-compat ./node ./index.mjs
```

用 `admin` / `Snowluma@2026` 登录后修改密码，按 Ctrl+C 结束，再从首页启动 SnowLuma。

### 手机访问不了 GitHub

安装器会尝试官方 Release 并自动重试。网络受限时可离线安装：把官方包 `SnowLuma-v2.2.1-linux-arm64.tar.gz` 通过应用的文件交换导入 `/android-share`，在 Ubuntu 终端执行：

```bash
mkdir -p /manager/extensions/snowluma && cp /android-share/SnowLuma-v2.2.1-linux-arm64.tar.gz /manager/extensions/snowluma/package.tar.gz
```

再回 SnowLuma 详情页点安装，安装器会复用校验通过的包。

### 版本说明

- 本项目固定 **SnowLuma v2.2.1**，这是完成手机端消息往返验证的版本。
- v2.3.x 更换了原生 hook，在 Linux QQ 3.2.34-53644 上注入会导致 QQ 进程退出；Android/PRoot 环境也不在上游可用性保证范围内。若要切换版本，请使用 `tools/snowluma-switch-2.2.1.sh`，它会保留配置与密码。

## 源码构建

### 环境

- JDK 17、Android SDK 35、Android NDK 27.0.12077973、Python 3.11+。
- 只编译源码：

```bash
./gradlew :core:compileKotlin :app:compileBootstrapReleaseKotlin
```

### 完整 APK

完整包需要运行镜像、原生库和发行资产。构建前准备 `runtimeAssetsDir`、`distributionAssetsDir`、`optionalAssetsDir` 和 `nativeLibsDir`（路径写入 `local.properties`），并在 `.local/signing/` 配置 PKCS12 签名，然后执行：

```bash
./gradlew -PbundledRuntime=true :app:assembleBundledRelease
```

### 校验

```bash
apksigner verify --verbose --min-sdk-version 21 app-bundled-release.apk
```

```bash
aapt2 dump badging app-bundled-release.apk
```

发布前确认 APK 仅包含 `arm64-v8a`、签名方案 v1/v2/v3 全部通过，且包内 `assets/bootstrap/arm64-v8a/ubuntu.tar.bin` 的 SHA-256 与 manifest 一致。

自行构建安装包有困难可以加 Q：2176317027。

## 常见问题

### 首页显示“本机端口 6185 已被占用”

旧版本在进程被系统强杀后可能留下 AstrBot 孤儿进程。新版本会在启动前和停止后自动清理并等待端口释放；如果仍然出现，检查是否有从终端手动启动的 AstrBot 实例。

### 桌面或服务运行一段时间后自动停止（退出码 137）

137 表示被系统强制杀死，常见原因是 Android 12+ 的后台子进程限制（默认约 32 个）或内存不足。请在 **设置 → 权限** 中使用 Shizuku 查看并调高后台子进程上限（256 为参考值），把应用加入息屏省电白名单，并关闭不用的终端与服务。

### SnowLuma 启动后 QQ 退出

确认使用的是 v2.2.1，而不是 2.3.x；v2.3.x 的原生 hook 在 Linux QQ 3.2.34-53644 上会触发此问题。必要时用 `tools/snowluma-switch-2.2.1.sh` 切回 v2.2.1。

### SnowLuma 后台密码错误或忘记

按上文“WebUI 密码”重置；安装/切换版本会保留 `config` 与 `data`，正常升级不会清除密码。

## 更新记录与文档

- [更新记录](./docs/CHANGELOG.md)
- [使用指南](./docs/使用指南.md)：后台配置、人设、网页、权限、扩展、资源和备份。
- [SnowLuma 扩展说明](./docs/SnowLuma扩展.md)：安装、启动、对接、离线安装、版本切换与排错。
- [构建指南](./docs/构建指南.md)
- [参与开发](.github/CONTRIBUTING.md) · [提交规则](.github/GIT_COMMIT_RULES.md)

## 贡献者

- **HZP-BT** — 项目维护者：SnowLuma 集成、首页服务卡、安装与发布流程
- JunWan666 — 上游项目作者（基础版本）

## 许可证与致谢

本项目原创代码采用 [MIT License](./LICENSE)。基础版本来自 [JunWan666/astrbot-for-android](https://github.com/JunWan666/astrbot-for-android)，感谢原作者与所有贡献者。

本项目不隶属、也不代表腾讯 / QQ 或 SnowLuma 官方。SnowLuma 采用源码可见非商业许可，原生组件受其 EULA 约束；第三方打包、自动化部署与商业使用需事先取得著作权人书面授权。QQ 客户端及相关组件的版权归腾讯公司所有。请仅将本项目用于个人学习与技术研究，遵守 QQ 用户协议与适用法律；使用非官方协议工具接入存在账号风险，请自行评估。

分享日志或截图时请隐藏账号、二维码、密码、Token 与 API Key。
