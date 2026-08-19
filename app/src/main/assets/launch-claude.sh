#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CONTAINER_NAME="claude-ubuntu"
PROJECTS_DIR="$HOME/claude-projects"

if ! command -v proot-distro >/dev/null 2>&1; then
    echo "proot-distro is not installed. Run setup from the Android app first."
    exit 1
fi

if ! proot-distro list -q | grep -Fxq "$CONTAINER_NAME"; then
    echo "Container '$CONTAINER_NAME' does not exist. Run setup from the Android app first."
    exit 1
fi

mkdir -p "$PROJECTS_DIR"

echo "Opening Claude Code..."
echo "Termux: $PROJECTS_DIR"
echo "Ubuntu: /workspace"

exec proot-distro login \
    --bind "$PROJECTS_DIR:/workspace" \
    --work-dir /workspace \
    "$CONTAINER_NAME" \
    -- bash -lc 'export PATH="$HOME/.local/bin:$PATH"; exec claude'
