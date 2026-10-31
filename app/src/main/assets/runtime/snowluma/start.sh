#!/bin/sh
set -eu
base=/manager/extensions/snowluma
program="$base/program"
desktop=/manager/extensions/desktop
test -x "$program/node"
test -f "$program/index.mjs"
export HOME=/persistent/snowluma/home
export XDG_CONFIG_HOME="$HOME/.config"
export XDG_DATA_HOME="$HOME/.local/share"
export XDG_CACHE_HOME="$HOME/.cache"
mkdir -p "$XDG_CONFIG_HOME" "$XDG_DATA_HOME" "$XDG_CACHE_HOME"
cd "$program"
# The QQ process loads the hook through the desktop startup profile. Disable the
# runtime's own autoload so the same QQ session is not hooked twice.
export SNOWLUMA_HOOK_AUTOLOAD=0
export SNOWLUMA_WEBUI_HOST=127.0.0.1
export SNOWLUMA_WEBUI_PORT=5099
exec /usr/bin/python3 "$desktop/guest-compat" ./node ./index.mjs
