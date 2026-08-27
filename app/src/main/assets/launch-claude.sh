#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CONTAINER_NAME="claude-ubuntu"
PROJECTS_DIR="$HOME/claude-projects"

# Without this, a failed precondition closes the Termux session instantly and
# the user never sees why.
fail() {
    echo
    echo "$1" >&2
    echo "Press Enter to close this session."
    read -r _ || true
    exit 1
}

if ! command -v proot-distro >/dev/null 2>&1; then
    fail "proot-distro is not installed. Run setup from the Android app first."
fi

if ! proot-distro list -q 2>/dev/null | grep -Fxq "$CONTAINER_NAME"; then
    fail "Container '$CONTAINER_NAME' does not exist. Run setup from the Android app first."
fi

mkdir -p "$PROJECTS_DIR"

echo "Opening Claude Code..."
echo "Termux: $PROJECTS_DIR"
echo "Ubuntu: /workspace"

# `exec` replaces this shell, so the trap-based pause above cannot apply here.
# Instead let the inner shell stay alive after claude exits, so authentication
# errors and crash output remain readable.
exec proot-distro login \
    --bind "$PROJECTS_DIR:/workspace" \
    --work-dir /workspace \
    "$CONTAINER_NAME" \
    -- bash -lc '
export PATH="$HOME/.local/bin:$PATH"

if ! command -v claude >/dev/null 2>&1; then
    echo "claude was not found in the container." >&2
    echo "Run setup from the Android app again." >&2
    echo "Press Enter to close this session."
    read -r _ || true
    exit 1
fi

claude || status=$?
status=${status:-0}
if [ "$status" -ne 0 ]; then
    echo
    echo "claude exited with code $status."
    echo "Press Enter to close this session."
    read -r _ || true
fi
exit "$status"
'
