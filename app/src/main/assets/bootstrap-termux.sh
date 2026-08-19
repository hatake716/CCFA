#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CONTAINER_NAME="claude-ubuntu"
IMAGE="ubuntu:24.04"
PROJECTS_DIR="$HOME/claude-projects"

echo "== Claude Code for Android: setup =="
echo "Updating Termux packages..."
pkg update -y

echo "Installing Termux dependencies..."
pkg install -y proot-distro git curl

mkdir -p "$PROJECTS_DIR"

if proot-distro list -q | grep -Fxq "$CONTAINER_NAME"; then
    echo "Container '$CONTAINER_NAME' already exists."
else
    echo "Installing Ubuntu 24.04 as '$CONTAINER_NAME'..."
    proot-distro install --name "$CONTAINER_NAME" "$IMAGE"
fi

echo "Installing Linux dependencies and Claude Code..."
proot-distro login "$CONTAINER_NAME" -- bash -lc '
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git ripgrep

if ! command -v claude >/dev/null 2>&1 && [ ! -x "$HOME/.local/bin/claude" ]; then
    echo "Installing Claude Code from Anthropic..."
    curl -fsSL https://claude.ai/install.sh | bash
fi

export PATH="$HOME/.local/bin:$PATH"
echo "Claude Code version:"
claude --version
'

echo
echo "Setup complete."
echo "Projects directory (Termux): $PROJECTS_DIR"
echo "Use the Android app's 'Claude Code を起動' button next."
