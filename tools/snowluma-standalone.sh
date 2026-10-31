#!/bin/bash
# Install and run the official SnowLuma Linux ARM64 release inside the
# AstrBot for Android Ubuntu terminal. Personal, non-commercial use only.
#
#   bash snowluma-standalone.sh install   # download + verify + extract + configure
#   bash snowluma-standalone.sh run       # foreground runtime (keep this terminal open)
#   bash snowluma-standalone.sh clear     # remove the QQ startup injection profile
#
# SnowLuma is source-available under a non-commercial license and its native
# components are bound by its EULA (section 5.4 requires prior written
# authorization for third-party packaging, automated deployment and commercial
# use). Read the official LICENSE and EULA before running this script.
set -eu

BASE=${SNOWLUMA_DIR:-/persistent/SnowLuma}
VERSION=2.3.1
URL="https://github.com/SnowLuma/SnowLuma/releases/download/v${VERSION}/SnowLuma-v${VERSION}-linux-arm64.tar.gz"
SHA256=ed9d2b765ff674da7e2cb3f105ec114e32d54ca1649fb87114dc7a3ed73719d2
DESKTOP=/manager/extensions/desktop

compat() { python3 "$DESKTOP/guest-compat" "$@"; }

install() {
    test -f "$DESKTOP/guest-compat" || { echo "请先在 App 中完成「Ubuntu 桌面」安装。" >&2; exit 1; }
    test "$(dpkg --print-architecture)" = arm64 || { echo "该包仅支持 ARM64。" >&2; exit 1; }
    mkdir -p "$BASE"
    cd "$BASE"
    current=""
    if test -f package.tar.gz; then current=$(sha256sum package.tar.gz | cut -d' ' -f1); fi
    if test "$current" != "$SHA256"; then
        rm -f package.tar.gz
        echo "正在下载 SnowLuma v$VERSION ..."
        curl -fL --retry 2 -o package.tar.gz "$URL"
        current=$(sha256sum package.tar.gz | cut -d' ' -f1)
    fi
    test "$current" = "$SHA256" || { echo "摘要不一致，已停止；请重新运行。" >&2; rm -f package.tar.gz; exit 1; }
    echo "摘要校验通过，正在解压 ..."
    tar -xzf package.tar.gz
    test -x node && test -f index.mjs && test -f native/snowluma-linux-arm64.so || { echo "发行包结构不完整。" >&2; exit 1; }
    compat configure --library "$BASE/native/snowluma-linux-arm64.so" \
        --symbol snowluma_linux_hook_start_dynamic --wait-module wrapper.node
    echo "完成。请先在 App 中重启原版 QQ，再执行：bash $0 run"
    echo "QQ 登录后访问 http://127.0.0.1:5099，密码见 run 的启动日志。"
}

run() {
    test -f "$BASE/index.mjs" || { echo "请先执行：bash $0 install" >&2; exit 1; }
    test -f "$DESKTOP/compat-profile.json" || { echo "未找到 QQ 启动注入配置，请先执行 install。" >&2; exit 1; }
    cd "$BASE"
    echo "保持此终端打开；按 Ctrl+C 停止 SnowLuma。"
    SNOWLUMA_HOOK_AUTOLOAD=0 SNOWLUMA_WEBUI_HOST=127.0.0.1 SNOWLUMA_WEBUI_PORT=5099 \
        exec python3 "$DESKTOP/guest-compat" ./node ./index.mjs
}

clear() {
    compat clear-profile
    echo "已清除 QQ 启动注入配置；SnowLuma 文件与账号数据保留。"
}

case "${1:-}" in
    install) install ;;
    run) run ;;
    clear) clear ;;
    *) echo "用法：bash $0 install|run|clear" >&2; exit 2 ;;
esac
