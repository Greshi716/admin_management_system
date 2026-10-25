#!/usr/bin/env bash
# Shared helpers for the Claude Code hooks. Reads the hook JSON from stdin.
INPUT="$(cat)"
PROJECT="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# json_field <name>: value of tool_input.<name> (empty if missing).
json_field() {
  local name="$1"
  if command -v node >/dev/null 2>&1; then
    printf '%s' "$INPUT" | FIELD="$name" node -e '
      let s=""; process.stdin.on("data",d=>s+=d).on("end",()=>{
        try { const j=JSON.parse(s); const v=(j.tool_input||{})[process.env.FIELD]; console.log(v==null?"":String(v)); }
        catch(e){ console.log(""); } });'
  else
    printf '%s' "$INPUT" | sed -n "s/.*\"$name\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" | head -1
  fi
}

# block <message>: tell Claude why and stop the tool call (exit code 2).
block() { echo "BLOCKED by project hook: $1" >&2; exit 2; }

current_branch() { git -C "$PROJECT" rev-parse --abbrev-ref HEAD 2>/dev/null; }

# is_git <command> <sub>: true when the command runs `git <sub>` (also git -C dir <sub>).
is_git() {
  printf '%s' "$1" | grep -Eq "(^|[;&|(\`[:space:]])git[[:space:]]+(-C[[:space:]]+[^[:space:]]+[[:space:]]+)?$2([[:space:]]|$)"
}
