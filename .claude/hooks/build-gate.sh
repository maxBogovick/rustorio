#!/bin/bash
# Stop: не дать завершить ход с красной сборкой.
#
# Зачем: «выглядит готовым» — единственный сигнал, который есть у агента без запускаемой
# проверки. Этот хук делает главный гейт обязательным, а не добровольным.
#
# Гоняется только когда в рабочем дереве есть изменённые .java — на обсуждениях,
# правках документации и ответах на вопросы ход не тормозится.
set -uo pipefail

# Ход уже был заблокирован этим хуком и агент пытается чиниться — второй блокировки не ставим.
# Без этой проверки сборка, которую агент починить не может (например, красная ещё до сессии),
# держит ход в петле до внутреннего лимита Claude Code: тот же полный build на каждом круге.
input=$(cat)
active=$(printf '%s' "$input" | /usr/bin/python3 -c \
  'import json,sys; print(json.load(sys.stdin).get("stop_hook_active", False))' 2>/dev/null)
[ "$active" = "True" ] && exit 0

cd "$CLAUDE_PROJECT_DIR" || exit 0

changed=$(git status --porcelain -- '*.java' 2>/dev/null)
[ -z "$changed" ] && exit 0

if ! out=$(./gradlew build --console=plain 2>&1); then
  printf 'Гейт ./gradlew build не прошёл — ход не завершён. Хвост вывода:\n%s\n' \
    "$(printf '%s' "$out" | tail -40)" >&2
  exit 2
fi
exit 0
