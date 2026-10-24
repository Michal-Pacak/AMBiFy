#!/usr/bin/env bash
# Builds the server and (re)starts it as the user's systemd service on the home server.
# The database lives outside the install directory, so redeploying never touches data.
set -euo pipefail

cd "$(dirname "$0")/../.."
./gradlew :server:test :server:installDist -q --console=plain

TARGET="$HOME/.local/opt/ambify-server"
mkdir -p "$TARGET" "$HOME/.local/share/ambify" "$HOME/.config/systemd/user"
rsync -a --delete server/build/install/server/ "$TARGET/"
cp server/deploy/ambify-server.service "$HOME/.config/systemd/user/"

systemctl --user daemon-reload
systemctl --user enable ambify-server.service
systemctl --user restart ambify-server.service

for _ in $(seq 1 30); do
    if curl -sf http://127.0.0.1:8080/health; then
        echo
        exit 0
    fi
    sleep 1
done
echo "Server did not answer on /health within 30 s" >&2
systemctl --user status ambify-server.service --no-pager >&2
exit 1
