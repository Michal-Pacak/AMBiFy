#!/usr/bin/env bash
# Builds the server and (re)starts the TEST server (port 18090, demo accounts) as a user service.
# --reset starts over with a fresh demo database.
set -euo pipefail

cd "$(dirname "$0")/../.."
./gradlew :server:installDist -q --console=plain

TARGET="$HOME/.local/opt/ambify-test"
DATA="$HOME/.local/share/ambify-test"
mkdir -p "$TARGET" "$DATA/backup" "$HOME/.config/systemd/user"
rsync -a --delete server/build/install/server/ "$TARGET/"
cp server/deploy/ambify-test.service "$HOME/.config/systemd/user/"

systemctl --user daemon-reload
systemctl --user enable ambify-test.service
if [ "${1:-}" = "--reset" ]; then
    systemctl --user stop ambify-test.service
    rm -f "$DATA"/ambify.db*
fi
systemctl --user restart ambify-test.service

for _ in $(seq 1 30); do
    if curl -sf http://127.0.0.1:18090/auth/status; then
        echo
        exit 0
    fi
    sleep 1
done
echo "Test server did not answer within 30 s" >&2
exit 1
