#!/bin/bash
# PostToolUse(Edit|Write): компиляция сразу после правки Java-файла.
#
# Зачем: правило «после каждой правки гоняй гейт» из AGENTS.md — рекомендация, которой
# агент может не последовать. Хук выполняется всегда и возвращает ошибку компиляции
# обратно агенту как обратную связь, пока контекст правки ещё свежий.
#
# Выход 0 — молча пропустить; выход 2 — сообщить агенту об ошибке (stderr уходит ему).
set -uo pipefail

input=$(cat)
file=$(printf '%s' "$input" | /usr/bin/python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)

# Не Java — не наше дело.
case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Тесты и src/tools компилируются своими задачами: NullAway на них выключен намеренно,
# но синтаксис проверить всё равно надо.
case "$file" in
  */src/test/*)  task=compileTestJava ;;
  */src/tools/*) task=compileToolsJava ;;
  *)             task=compileJava ;;
esac

if ! out=$(./gradlew "$task" -q --console=plain 2>&1); then
  printf 'Компиляция (%s) не прошла после правки %s:\n%s\n' "$task" "$file" "$out" >&2
  exit 2
fi
exit 0
