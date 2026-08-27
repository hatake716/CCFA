#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CONTAINER_NAME="claude-ubuntu"
IMAGE="ubuntu:24.04"
PROJECTS_DIR="$HOME/claude-projects"

# The Android app starts this in a fresh Termux session. Keep the session open
# on failure so the user can actually read the error instead of watching the
# terminal disappear.
on_exit() {
    status=$?
    echo
    if [ "$status" -ne 0 ]; then
        echo "== Setup FAILED (exit code $status) =="
        echo "Scroll up to see the error, then re-run setup from the app."
    fi
    echo "Press Enter to close this session."
    read -r _ || true
}
trap on_exit EXIT

echo "== Claude Code for Android: setup =="

echo "Updating Termux packages..."
pkg update -y

echo "Installing Termux dependencies..."
pkg install -y proot-distro git curl

mkdir -p "$PROJECTS_DIR"

if proot-distro list -q 2>/dev/null | grep -Fxq "$CONTAINER_NAME"; then
    echo "Container '$CONTAINER_NAME' already exists."
else
    echo "Installing Ubuntu 24.04 as '$CONTAINER_NAME'..."
    proot-distro install --name "$CONTAINER_NAME" "$IMAGE"
fi

echo "Installing Linux dependencies and Claude Code..."
# The inner script is single-quoted, so $HOME/$PATH expand inside the container
# rather than in the Termux shell.
proot-distro login "$CONTAINER_NAME" -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git ripgrep

export PATH="$HOME/.local/bin:$PATH"

if ! command -v claude >/dev/null 2>&1; then
    echo "Installing Claude Code from Anthropic..."
    curl -fsSL https://claude.ai/install.sh | bash
    # The installer drops the binary into ~/.local/bin, which the PATH export
    # above already covers. Re-hash so this shell picks it up immediately.
    hash -r
fi

if ! command -v claude >/dev/null 2>&1; then
    echo "Claude Code was installed but is not on PATH." >&2
    exit 1
fi

echo "Claude Code version:"
claude --version
'

echo
echo "Setup complete."
echo "Projects directory (Termux): $PROJECTS_DIR"
echo "Use the Android app's 'Claude Code を起動' button next."
