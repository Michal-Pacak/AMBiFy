#!/usr/bin/env bash
# Builds the server, installs it in /opt/ambify-server and restarts the system service.
# The database lives in /var/lib/ambify, so redeploying never touches data.
# Restarting relies on the narrow sudo rule from sudoers-ambify-deploy (no password).
set -euo pipefail

cd "$(dirname "$0")/../.."
./gradlew :server:test :server:installDist -q --console=plain

rsync -a --delete server/build/install/server/ /opt/ambify-server/
sudo -n /usr/bin/systemctl restart ambify-server.service

for _ in $(seq 1 30); do
    if curl -sf http://127.0.0.1:8080/health; then
        echo
        exit 0
    fi
    sleep 1
done
echo "Server did not answer on /health within 30 s" >&2
systemctl status ambify-server.service --no-pager >&2
exit 1
