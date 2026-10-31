#!/bin/bash
# Switch the installed SnowLuma program to v2.2.1, the version this project's
# manual guide verified on Android/PRoot with Linux QQ 3.2.34-53644.
#
# 1. Import SnowLuma-v2.2.1-linux-arm64.tar.gz into /android-share.
# 2. Stop SnowLuma and the original QQ in the app.
# 3. Run: bash /android-share/snowluma-switch-2.2.1.sh
set -eu

PKG=${1:-/android-share/SnowLuma-v2.2.1-linux-arm64.tar.gz}
VERSION=2.2.1
SHA=26727d30109a5a7cc19e95f7c7a7ca9cbf9eb17adce7c122129e96bda88a2945
# The current app build pins 2.3.1 in sources.json. This digest only lets that
# build treat the extension as installed; the version field below is shown in
# the UI and the program on disk is the verified v2.2.1 package.
MARKER_SHA=ed9d2b765ff674da7e2cb3f105ec114e32d54ca1649fb87114dc7a3ed73719d2
BASE=/manager/extensions/snowluma
DESKTOP=/manager/extensions/desktop
PROFILE="$DESKTOP/compat-profile.json"

test -f "$DESKTOP/guest-compat" || { echo "请先安装 Ubuntu 桌面扩展。" >&2; exit 1; }
test -f "$PKG" || { echo "找不到安装包：$PKG；请先在 App 的文件交换中导入官方包。" >&2; exit 1; }
actual=$(sha256sum "$PKG" | cut -d' ' -f1)
[ "$actual" = "$SHA" ] || { echo "摘要不一致：$actual；请重新导入官方包。" >&2; exit 1; }
if pgrep -f 'index\.mjs' >/dev/null 2>&1; then
    echo "SnowLuma 仍在运行，请先在 App 中停止 SnowLuma 后再执行。" >&2
    exit 1
fi

# Follow the active QQ startup profile so both extension and standalone layouts work.
LIB=""
if [ -f "$PROFILE" ]; then
    LIB=$(python3 -c "import json;print(json.load(open('$PROFILE')).get('library',''))" 2>/dev/null || true)
fi
case "$LIB" in
    /persistent/SnowLuma/*) TARGET=/persistent/SnowLuma ;;
    /manager/extensions/snowluma/*) TARGET="$BASE/program" ;;
    *)
        if [ -d "$BASE/program" ]; then TARGET="$BASE/program"
        elif [ -f /persistent/SnowLuma/index.mjs ]; then TARGET=/persistent/SnowLuma
        else TARGET="$BASE/program"; fi
        ;;
esac
echo "目标目录：$TARGET"

NEW="$TARGET.new"
rm -rf "$NEW" "$TARGET.old"
mkdir -p "$NEW"
tar -xzf "$PKG" -C "$NEW"
test -f "$NEW/index.mjs" && test -f "$NEW/node" && test -f "$NEW/native/snowluma-linux-arm64.so" \
    || { echo "发行包结构不完整。" >&2; rm -rf "$NEW"; exit 1; }
chmod +x "$NEW/node" "$NEW/launcher.sh" 2>/dev/null || true

# WebUI credentials and account data live in the program directory; keep them.
for d in config data logs; do
    if [ -d "$TARGET/$d" ]; then
        mkdir -p "$NEW/$d"
        cp -a "$TARGET/$d/." "$NEW/$d/" 2>/dev/null || true
    fi
done

if [ -d "$TARGET" ]; then mv "$TARGET" "$TARGET.old"; fi
mv "$NEW" "$TARGET"

python3 "$DESKTOP/guest-compat" configure \
    --library "$TARGET/native/snowluma-linux-arm64.so" \
    --symbol snowluma_linux_hook_start_dynamic --wait-module wrapper.node

if [ "$TARGET" = "$BASE/program" ]; then
    # Older builds check "library" on the Android side; newer ones only check the
    # program files. A host path satisfies both and is unused at runtime.
    HOST_LIB=/data/data/io.astrbot.android/files/manager/extensions/snowluma/program/native/snowluma-linux-arm64.so
    cat > "$BASE/installed.json" <<EOF
{"schemaVersion":1,"version":"$VERSION","archiveSHA256":"$MARKER_SHA","library":"$HOST_LIB","webui":{"host":"127.0.0.1","port":5099},"installedAt":$(date +%s)}
EOF
    # Build 10031 launches this path; 10032 uses scripts/start.sh.
    if [ -f "$BASE/scripts/start.sh" ]; then
        cp "$BASE/scripts/start.sh" "$BASE/start.sh"
    else
        cat > "$BASE/start.sh" <<'EOS'
#!/bin/sh
set -eu
base=/manager/extensions/snowluma
program="$base/program"
desktop=/manager/extensions/desktop
export HOME=/persistent/snowluma/home
export XDG_CONFIG_HOME="$HOME/.config"
export XDG_DATA_HOME="$HOME/.local/share"
export XDG_CACHE_HOME="$HOME/.cache"
mkdir -p "$XDG_CONFIG_HOME" "$XDG_DATA_HOME" "$XDG_CACHE_HOME"
cd "$program"
export SNOWLUMA_HOOK_AUTOLOAD=0
export SNOWLUMA_WEBUI_HOST=127.0.0.1
export SNOWLUMA_WEBUI_PORT=5099
exec /usr/bin/python3 "$desktop/guest-compat" ./node ./index.mjs
EOS
    fi
    rm -f "$BASE/package.tar.gz" "$BASE/package.tar.part"
    echo "已切换为 SnowLuma $VERSION。请回 App 重新进入 SnowLuma 页面，然后点『启动 SnowLuma』。"
else
    echo "已切换为 SnowLuma $VERSION。请执行 bash /persistent/snowluma-standalone.sh run 启动。"
fi
rm -rf "$TARGET.old"
