"""Отправляет текст из stdin в Discord-вебхук (переменная DISCORD_WEBHOOK). Обрезает до лимита 2000 символов."""
import json
import os
import sys
import urllib.request

text = sys.stdin.read().strip()
if len(text) > 2000:
    text = text[:1997] + "..."
req = urllib.request.Request(
    os.environ["DISCORD_WEBHOOK"],
    data=json.dumps({"content": text}).encode(),
    headers={"Content-Type": "application/json", "User-Agent": "encrypttext-ci"},
)
urllib.request.urlopen(req, timeout=30).read()
