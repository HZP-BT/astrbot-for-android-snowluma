#!/usr/bin/env python3
"""Set the SnowLuma WebUI password in the installed program's config/webui.json.

SnowLuma stores scrypt(password, salt, 64, N=16384, r=8, p=1). Writing the
state directly lets the management app reset or change the password while the
runtime is stopped, without asking the user to edit the WebUI first.
"""
import hashlib
import json
import os
from pathlib import Path
import sys
import time

CONFIG = Path('/manager/extensions/snowluma/program/config/webui.json')


def strong(password):
    return (len(password) >= 10
            and not any(ch.isspace() for ch in password)
            and any(ch.islower() for ch in password)
            and any(ch.isupper() for ch in password)
            and any(not ch.isalnum() for ch in password))


def main():
    if len(sys.argv) != 2 or not strong(sys.argv[1]):
        raise SystemExit('usage: password.py <password with >=10 chars, upper/lower/special, no spaces>')
    password = sys.argv[1]
    salt = os.urandom(16)
    digest = hashlib.scrypt(password.encode('utf-8'), salt=salt, n=16384, r=8, p=1,
                            dklen=64, maxmem=64 * 1024 * 1024)
    state = {}
    if CONFIG.is_file():
        try:
            value = json.loads(CONFIG.read_text())
            if isinstance(value, dict):
                state = value
        except (OSError, ValueError):
            state = {}
    now = time.strftime('%Y-%m-%dT%H:%M:%S.000Z', time.gmtime())
    state.update(passwordHash=digest.hex(), passwordSalt=salt.hex(),
                 mustChangePassword=False,
                 generatedAt=state.get('generatedAt', now), updatedAt=now)
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    temporary = CONFIG.with_name(CONFIG.name + '.tmp')
    temporary.write_text(json.dumps(state, indent=2))
    os.chmod(temporary, 0o600)
    os.replace(temporary, CONFIG)
    print('password updated')


if __name__ == '__main__':
    main()
