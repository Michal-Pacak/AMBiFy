#!/usr/bin/env bash
# One-time setup of the AMBiFy server as a system service (run with sudo).
# Safe to run again: existing account and directories are kept.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Run with sudo: sudo bash $0" >&2
    exit 1
fi

DEPLOY_USER="${SUDO_USER:-mlody}"
HERE="$(cd "$(dirname "$0")" && pwd)"
BACKUP_PARENT=/mnt/nas/Backup/serverek
BACKUP_DIR="$BACKUP_PARENT/ambify"

step() { echo; echo "==> $*"; }

step "Account 'ambify' (system, no login)"
if id ambify >/dev/null 2>&1; then
    echo "already exists"
else
    useradd --system --home-dir /var/lib/ambify --no-create-home --shell /usr/sbin/nologin ambify
fi

step "Database directory /var/lib/ambify (only ambify)"
install -d -o ambify -g ambify -m 700 /var/lib/ambify

step "Program directory /opt/ambify-server (owned by $DEPLOY_USER, ambify can only run it)"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" -m 755 /opt/ambify-server

step "Backup directory $BACKUP_DIR on the NAS"
if ! findmnt /mnt/nas >/dev/null; then
    echo "NAS is not mounted at /mnt/nas - mount it first (sudo mount /mnt/nas)" >&2
    exit 1
fi
install -d -m 711 "$BACKUP_PARENT"
install -d -o ambify -g ambify -m 700 "$BACKUP_DIR"

step "sudo rule: $DEPLOY_USER may only restart the AMBiFy services"
visudo -cf "$HERE/sudoers-ambify-deploy"
install -m 440 -o root -g root "$HERE/sudoers-ambify-deploy" /etc/sudoers.d/ambify-deploy

step "systemd units"
install -m 644 "$HERE/ambify-server.service" "$HERE/ambify-backup.service" "$HERE/ambify-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable ambify-server.service
systemctl enable --now ambify-backup.timer

echo
echo "Done. The server starts after the first deploy (server/deploy/deploy.sh)."
