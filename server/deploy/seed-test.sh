#!/usr/bin/env bash
# Dane testowe dla serwera TEST (port 18090): zajęcia dodatkowe Filipa i Bartosza w „Społeczności testowej”.
# Każdy nowy widget/moduł dopisuje tu swoje dane, żeby Michał miał co testować. Uruchamiaj po deploy-test.sh --reset.
set -euo pipefail
B=http://127.0.0.1:18090
T=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"owner@test.ambify","password":"test1234","deviceName":"seed-test"}' \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
students=$(curl -s $B/timetable -H "Authorization: Bearer $T")
id() { echo "$students" | python3 -c "import sys,json;print(next(s['id'] for s in json.load(sys.stdin)['students'] if s['name']=='$1'))"; }
F=$(id Filip); R=$(id Bartosz)
monday=$(date -d "monday this week" +%F)
day() { date -d "$monday +$1 day" +%F; }
W() { echo "{\"frequency\":\"WEEKLY\",\"weekdays\":[$1],\"skipSchoolFree\":true}"; }
existing=$(python3 -c 'import sqlite3,os;print("|".join(n for (n,) in sqlite3.connect(os.path.expanduser("~/.local/share/ambify-test/ambify.db")).execute("select name from extra_activities")))')
add() {
    # Run again (e.g. for newer test data below): the activities already there are not added twice.
    case "|$existing|" in *"|$2|"*) echo "jest już: $2"; return ;; esac
    curl -s -o /dev/null -w "%{http_code} $2\n" -X POST $B/timetable/extras -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
        -d "{\"studentIds\":[\"$1\"],\"name\":\"$2\",\"place\":\"$3\",\"startDate\":\"$4\",\"startTime\":\"$5\",\"endTime\":\"$6\",\"recurrence\":$7}"
}
add "$F" "Basen" "Aquapark Wesoła" "$(day 0)" 16:00 17:00 "$(W 1,3)"
add "$F" "Piłka nożna" "Orlik przy SP 12" "$(day 1)" 17:30 19:00 "$(W 2,4)"
add "$F" "Angielski" "Szkoła językowa Easy" "$(day 4)" 15:00 16:00 "$(W 5)"
add "$R" "Gitara" "Dom Kultury" "$(day 0)" 18:00 18:45 "$(W 1)"
add "$R" "Szachy" "Klub Hetman" "$(day 2)" 15:30 17:00 "$(W 3)"
add "$R" "Robotyka i programowanie LEGO" "Centrum Kreatywności" "$(day 3)" 16:00 17:30 "$(W 4)"
add "$F" "Wycieczka: Centrum Nauki Kopernik" "Warszawa" "$(day 5)" 10:00 14:00 '{"frequency":"NONE"}'

# Zmiany w planie Bartosza na najbliższy dzień szkolny (widget „Jutro w szkole”: Średnia pokazuje 3, Duża do 6).
python3 - "$R" <<'PY'
import sqlite3, sys, os
c = sqlite3.connect(os.path.expanduser("~/.local/share/ambify-test/ambify.db"))
sid = sys.argv[1]
day = c.execute("select min(date) from lessons where student_id=? and date>date('now','localtime')", (sid,)).fetchone()[0]
pos = [p for (p,) in c.execute("select position from lessons where student_id=? and date=? order by position", (sid, day))]
for i, p in enumerate(pos[:5]):
    kind, note = ("CANCELLED", "Odwołana") if i in (0, 4) else ("SUBSTITUTION", "Zastępstwo")
    c.execute("update lessons set change_kind=?, change_note=? where student_id=? and date=? and position=?", (kind, note, sid, day, p))
c.commit(); print("zmiany Bartosza:", day, len(pos[:5]))
PY

# Zdjęcia profilowe (0.7.6): Ola i Ula ze zdjęciem (Ula = Filip na widgetach), Adam (= Bartosz) ze zdjęciem
# ukrytym przez Olę — na inicjałach i na liście „Ukryte zdjęcia”.
HERE=$(dirname "$0")
TMP=$(mktemp -d)
login() {
    curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
        -d "{\"email\":\"$1@test.ambify\",\"password\":\"test1234\",\"deviceName\":\"seed-test\"}" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])'
}
photo() {
    "$HERE/seed-portrait.py" "$3" "$4" "$5" "$6" "$TMP/$1.png"
    curl -s -o /dev/null -w "%{http_code} zdjęcie $1\n" -X PUT $B/me/photo -H "Authorization: Bearer $2" -H 'Content-Type: image/png' --data-binary "@$TMP/$1.png"
}
TO=$(login owner); TA=$(login admin); TU=$(login user)
photo owner "$TO" 8AA6C2 E2B99A 5D6E86 6B4A2E
photo user "$TU" E8C27A F0C8A8 9B5C4A 3A2A1E
photo admin "$TA" 9AD7A4 D9A57E 2F5F4A 1E1E1E
ADAM=$(curl -s $B/me -H "Authorization: Bearer $TA" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -s -o /dev/null -w "%{http_code} ukryte zdjęcie Adama\n" -X PUT "$B/family/members/$ADAM/photo-hidden" \
    -H "Authorization: Bearer $TO" -H 'Content-Type: application/json' -d '{"hidden":true}'
rm -rf "$TMP"

# Motywy (0.7.7): „Społeczność testowa” ma Premium motywów i własny motyw „Szalik TEST” (wzór na tapecie) — Ola
# (Owner) może go ustawić i zablokować. Ola ma też Premium konta i własny motyw „Legia”; Ula i Adam bez Premium konta
# (Ula widzi Kod/Gaming tylko dzięki Premium społeczności).
TP=$(login platform)
CID=$(curl -s $B/me -H "Authorization: Bearer $TO" | python3 -c 'import sys,json;print(json.load(sys.stdin)["communityId"])')
curl -s -o /dev/null -w "%{http_code} Premium motywów dla społeczności\n" -X PUT "$B/platform/communities/$CID/grants/THEMES" \
    -H "Authorization: Bearer $TP" -H 'Content-Type: application/json' -d '{"granted":true}'
curl -s -o /dev/null -w "%{http_code} Premium konta: Ola\n" -X POST $B/themes/platform/premium \
    -H "Authorization: Bearer $TP" -H 'Content-Type: application/json' -d '{"email":"owner@test.ambify","premium":true}'
curl -s -o /dev/null -w "%{http_code} motyw Legia\n" -X PUT $B/themes/own -H "Authorization: Bearer $TO" -H 'Content-Type: application/json' \
    -d '{"id":"legia-test-01","name":"Legia","dark":true,"bgHue":150,"bgSat":0,"colors":["#F7F7F7","#00733E","#D7142A"],"style":"STRIPES","effect":"hit","wallpaper":{"pattern":"scarf","dim":84}}'
curl -s -o /dev/null -w "%{http_code} motyw społeczności\n" -X PUT "$B/themes/own?community=1" -H "Authorization: Bearer $TO" -H 'Content-Type: application/json' \
    -d '{"id":"szalik-test-01","name":"Szalik TEST","dark":true,"bgHue":225,"bgSat":45,"colors":["#D4AF37","#1B3580","#F7F7F7"],"style":"METAL","effect":"sparks","wallpaper":{"pattern":"diag","dim":86}}'
