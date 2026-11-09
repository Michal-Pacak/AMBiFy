#!/usr/bin/env bash
# Builds the app and publishes it for download and self-update: the APK and latest.json in the server's releases
# directory. Phones see it at once (the server reads latest.json on every request); no restart.
#   publish-app.sh                 the real app, for the real server — only after Michał accepted the version
#   publish-app.sh --test          AMBiFy TEST, for the test server (port 18090)
#   publish-app.sh --kids          build dla telefonów dzieci → osobny kanał "kids" (/app/kids/..., /pobierz/kids)
#   --min N                        apps older than versionCode N must update before they work again
# Anything else goes to Gradle, e.g. -Pambify.versionCode=10 -Pambify.versionName=0.6.2 for a trial update.
# "Co nowego" comes from app/whatsnew.txt, one point per line.
set -euo pipefail

cd "$(dirname "$0")/../.."
TEST=0
KIDS=0
MIN=0
GRADLE_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --test) TEST=1 ;;
        --kids) KIDS=1 ;;
        --min) MIN="$2"; shift ;;
        *) GRADLE_ARGS+=("$1") ;;
    esac
    shift
done

if [ "$TEST" = 1 ]; then
    GRADLE_ARGS+=("-Pambify.serverUrl=http://192.168.1.122:18090")
    DIR="$HOME/.local/share/ambify-test/releases"
    URL="http://127.0.0.1:18090"
else
    DIR="/opt/ambify-server/releases"
    URL="http://127.0.0.1:8080"
fi

# Kanał kids: osobny build (-Pambify.kids=true), osobny podkatalog i osobny endpoint kontrolny.
if [ "$KIDS" = 1 ]; then
    GRADLE_ARGS+=("-Pambify.kids=true")
    DIR="$DIR/kids"
    CHECK="/app/kids/latest"
else
    CHECK="/app/latest"
fi

./gradlew :app:assembleDebug -q --console=plain "${GRADLE_ARGS[@]}"

OUT=app/build/outputs/apk/debug
mkdir -p "$DIR"
python3 - "$OUT" "$DIR" "$MIN" app/whatsnew.txt <<'PY'
import datetime, hashlib, json, os, shutil, sys
out, target, minimum, notes_file = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
element = json.load(open(os.path.join(out, "output-metadata.json")))["elements"][0]
name = "ambify-%s-%d.apk" % (element["versionName"], element["versionCode"])
shutil.copyfile(os.path.join(out, element["outputFile"]), os.path.join(target, name))
data = open(os.path.join(target, name), "rb").read()
notes = [line.strip() for line in open(notes_file, encoding="utf-8") if line.strip()] if os.path.exists(notes_file) else []
release = {
    "versionCode": element["versionCode"], "versionName": element["versionName"], "notes": notes, "file": name,
    "sha256": hashlib.sha256(data).hexdigest(), "sizeBytes": len(data), "minVersionCode": minimum,
    "publishedAt": datetime.date.today().isoformat(),
}
with open(os.path.join(target, "latest.json.tmp"), "w", encoding="utf-8") as f:
    json.dump(release, f, ensure_ascii=False, indent=2)
os.replace(os.path.join(target, "latest.json.tmp"), os.path.join(target, "latest.json"))
# Keep the three newest APKs.
apks = sorted((p for p in os.listdir(target) if p.startswith("ambify-") and p.endswith(".apk")),
              key=lambda p: os.path.getmtime(os.path.join(target, p)), reverse=True)
for old in apks[3:]:
    os.remove(os.path.join(target, old))
print("Opublikowano %s (versionCode %d) w %s" % (element["versionName"], element["versionCode"], target))
PY

curl -sf "$URL$CHECK" && echo || echo "Uwaga: serwer ($URL) nie odpowiada — plik czeka na jego start." >&2
