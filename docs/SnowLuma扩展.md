# SnowLuma 扩展：在 App 内安装、启动与对接 AstrBot

本构建在 **设置 → 扩展组件 → QQ 接入** 新增 **SnowLuma** 扩展，把原来需要手动执行的下载、校验、解压、`guest-compat` 启动注入和 Node 进程启动合并为「安装 → 启动 → 打开后台」三步。

> **先看许可。** SnowLuma 采用[源码可见非商业许可](https://github.com/SnowLuma/SnowLuma/blob/v2.3.1/LICENSE)，不是 OSI 开源许可；原生组件（`snowluma-*.so`、`snowluma-*.node`）另受 [EULA](https://github.com/SnowLuma/SnowLuma/blob/v2.3.1/EULA.md) 第 5 条约束。EULA 第 5.4 条要求：将专有组件**并入第三方安装包、通过自动化脚本部署**，或用于商业用途，均须事先取得著作权人的**书面授权**。
>
> 本扩展按「设备持有人个人非商业自用、安装时由用户发起并从官方 Release 下载原包」的方式工作，不随 APK 分发 SnowLuma 程序、原生库或修改版。**公开分发包含本扩展的构建前，请先联系 `motricseven@foxmail.com` 取得授权。** 上游对本 App 或本扩展没有背书。

## 工作方式

SnowLuma 不替换 `opt/qq` 里的 NapCat：它使用扩展组件中的**原版 Linux QQ**（独立安装、独立登录数据），通过桌面的 `guest-compat` 在 QQ 启动时注入 `snowluma-linux-arm64.so`，再由扩展托管官方包自带的 Node.js 运行 `index.mjs`。WebUI 监听 `127.0.0.1:5099`，OneBot 以反向 WebSocket 连到 AstrBot 的 `127.0.0.1:6199`。

```
原版 QQ（桌面）── 注入 hook ──► SnowLuma 运行时 ── OneBot v11 WS ──► AstrBot ──► 大模型
```

## 前置条件

- ARM64 手机，Android 8.0 及以上，已完成 Ubuntu 运行环境初始化。
- 在 **扩展组件 → 终端与桌面 → Ubuntu 桌面** 安装桌面依赖（含 `guest-compat` 与编译工具）。
- 在同一扩展的 **原版 QQ** 页安装原版 QQ（当前锁定 3.2.34-53644）。
- 运行 SnowLuma 时不要再点首页的「启动全部」：napcat 与 SnowLuma 互斥，扩展会自行处理。

## 安装与对接

1. 打开 **设置 → 扩展组件 → QQ 接入 → SnowLuma**。
2. 阅读许可说明，勾选确认后点「确定」，安装入口才会出现。
3. 点 **安装 SnowLuma**。App 从官方 Release 下载 `SnowLuma-v2.3.1-linux-arm64.tar.gz`（约 50 MiB），先按 `sources.json` 中的 SHA-256 校验，再解压到私有目录并写入 QQ 启动注入配置。
4. 安装完成后点 **启动 SnowLuma**。App 会自动启动 Ubuntu 桌面与原版 QQ；回到桌面页打开桌面，在 QQ 窗口扫码登录。
5. 点 **打开 SnowLuma 后台**（`http://127.0.0.1:5099`），用扩展日志中的初始密码登录，并按页面要求修改密码。
6. 在 SnowLuma 后台为已登录账号添加 **WS 客户端（反向连接）**：地址 `ws://127.0.0.1:6199/ws`，角色 `Universal`，消息格式 `array`，Token 与 AstrBot 的 OneBot 连接一致（默认留空）。
7. 回首页启动 **AstrBot**，用另一个 QQ 账号私聊测试，确认消息往返。

## 与 NapCat 的关系

- **互斥**：启动 SnowLuma 会停止 NapCat 并把 NapCat 的启动偏好置为关闭；启动 NapCat 会停止 SnowLuma。同一时间只有一个 QQ 会话被桥接。
- **启动全部**：成功启动过一次 SnowLuma 后，首页「启动全部」会优先启动 AstrBot + SnowLuma（桌面与 QQ 一并拉起）；之后单独启动 NapCat 会切回 NapCat，并让「启动全部」恢复默认。
- **AstrBot 连接不变**：默认的 `aiocqhttp` 连接监听 `127.0.0.1:6199`，NapCat 与 SnowLuma 都作为反向 WS 客户端接入，不需要改名或重建连接。
- **首页计数**：首页仍显示 AstrBot / NapCat 两项；使用 SnowLuma 时 NapCat 保持停止，显示 `1/2` 是预期状态。QQ 登录与接入状态在 SnowLuma 后台查看。
- **登录数据**：原版 QQ 位于 `/manager/extensions/desktop/qq`，与镜像内 `/opt/qq` 的 NapCat 是两份独立安装，账号数据互不影响。

## 文件与数据

| 内容 | 位置 |
| --- | --- |
| 程序（官方原包解压） | `/manager/extensions/snowluma/program` |
| 下载来源与摘要（锁定） | `app/src/main/assets/runtime/snowluma/sources.json` |
| 账号与运行数据 | `/persistent/snowluma/home` |
| QQ 启动注入配置 | `/manager/extensions/desktop/compat-profile.json` |
| 运行日志 | 设置 → 日志与诊断 → 日志 → SnowLuma |

卸载会停止 SnowLuma、清除注入配置并删除程序，保留 `/persistent/snowluma` 下的账号数据。

## 不重新打包 APK：在现有版本上使用

如果暂时不构建 APK，可用仓库中的 `tools/snowluma-standalone.sh` 在已安装的 App 里完成同样的安装与启动：

1. 将脚本传入手机，通过 App 的文件导入放入 `/android-share`。
2. 打开 Ubuntu 终端，执行 `cp /android-share/snowluma-standalone.sh /persistent/`，再执行 `bash /persistent/snowluma-standalone.sh install`。
3. 在 App 中停止并重新启动原版 QQ，让注入配置生效并登录。
4. 执行 `bash /persistent/snowluma-standalone.sh run` 并保持终端打开；之后按上文登录 `http://127.0.0.1:5099` 并连接 AstrBot。
5. 停止时先在终端按 Ctrl+C，再停止原版 QQ。运行 `bash /persistent/snowluma-standalone.sh clear` 可清除 QQ 注入配置。

该脚本同样是从官方 Release 下载原包，适用于设备持有人个人非商业自用；公开分发前仍需取得授权。

## 自行构建完整 APK

完整包需要仓库外的运行镜像与原生库。本次构建采用官方 1.3.0 APK 中已发布的同版本资源：

1. 从 [官方 v1.3.0 Release](https://github.com/JunWan666/astrbot-for-android/releases/tag/v1.3.0) 下载 arm64 APK，先用 `apksigner verify --print-certs` 确认签名者为 `CN=AstrBot for Android, O=JunWan666`。
2. 解出 `assets/bootstrap/arm64-v8a/`、`assets/third-party/`、`assets/optional/` 和 `lib/arm64-v8a/`，分别作为 `runtimeAssetsDir` 的 `bootstrap/arm64-v8a`、`distributionAssetsDir`、`optionalAssetsDir`、`nativeLibsDir`。
3. 在 `.local/signing/` 生成独立签名（`release.p12` + `release.properties`，`storeType` 为 PKCS12）。
4. 在 `local.properties` 写入上述路径，然后执行：

```bash
./gradlew -PbundledRuntime=true -PappVersionCode=10031 -PappVersionName=1.3.0-snowluma :app:assembleBundledRelease
```

5. 校验产物：`apksigner verify --verbose --min-sdk-version 21`（v1/v2/v3 均应为 true）、`aapt2 dump badging`（仅 `arm64-v8a`）、以及 APK 内 `assets/bootstrap/arm64-v8a/ubuntu.tar.bin` 的 SHA-256 与 manifest 一致。

新签名与官方包不同，安装前必须先卸载官方 App；卸载会删除应用数据，请先在官方 App 中导出便携备份，安装新包并初始化运行环境后再导入。包含官方运行镜像的完整包仅供个人使用，不要公开分发。

## 常见问题

- **提示先安装 Ubuntu 桌面或原版 QQ**：扩展依赖桌面的 `guest-compat` 与原版 QQ，先在「Ubuntu 桌面」扩展中完成安装。
- **5099 打不开**：查看 SnowLuma 日志与扩展日志；确认原版 QQ 已登录且 SnowLuma 处于运行状态。同一时间只运行一份 SnowLuma。
- **启动后不久自动停止**：原版 QQ 退出或桌面被停止时，SnowLuma 会随之关闭；先在桌面中重新登录 QQ，再点启动。
- **安装提示摘要或大小不一致**：下载不完整或来源异常，重新安装即可；也可对照官方 Release 附件的 SHA-256 与 `sources.json`。
- **QQ 在线、反向连接成功但不回复**：依次检查 SnowLuma 账号在线、WS 客户端 URL/Token/角色/消息格式、AstrBot 模型测试；先用另一个账号私聊判断问题在哪一段。
- **端口冲突**：5099 被占用时先停止占用程序；6199 被 AstrBot 监听属正常，不要把 SnowLuma 的监听端口改成 6199。

## 更新版本

`sources.json` 固定版本、下载地址、字节数与 SHA-256。升级时修改该文件中的 `version`、`asset` 与 `hook` 字段，并同步更新 `docs/SnowLuma手动部署.md` 与本文；扩展按摘要判断是否已安装，重新点「安装」即可覆盖，账号数据保留。

## 来源与许可

- [SnowLuma 官方仓库](https://github.com/SnowLuma/SnowLuma)与[v2.3.1 发行页](https://github.com/SnowLuma/SnowLuma/releases/tag/v2.3.1)
- [官方部署指南](https://snowluma.github.io/zh/docs/guide/deploy)与[动作参考](https://snowluma.github.io/zh/docs/api)
- [v2.3.1 LICENSE](https://github.com/SnowLuma/SnowLuma/blob/v2.3.1/LICENSE)、[v2.3.1 EULA](https://github.com/SnowLuma/SnowLuma/blob/v2.3.1/EULA.md)
- 原手动流程见 [SnowLuma 手机手动部署](SnowLuma手动部署.md)

本项目的 MIT 许可适用于本项目原创内容，不为 SnowLuma 或 QQ 授予额外权利。分享排错资料时请隐藏账号、二维码、密码、Token 与 API Key。
