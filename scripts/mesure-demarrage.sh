#!/usr/bin/env bash
# Installe un APK de Kairo par-dessus l'existant et mesure le premier affichage de l'accueil.
# Usage : bash scripts/mesure-demarrage.sh <ip> <apk>
# Ne touche ni au réseau, ni à la veille, ni à l'écran de veille de la box. Pas de force-stop :
# c'est l'installation qui arrête l'appli, d'où un démarrage à froid.
set -euo pipefail

IP="${1:?usage: mesure-demarrage.sh <ip> <apk>}"
APK="${2:?usage: mesure-demarrage.sh <ip> <apk>}"
PKG=com.vantaflow.kairo
ADB=(adb -s "$IP:5555")

"${ADB[@]}" install -r "$APK" >/dev/null
"${ADB[@]}" shell cmd package compile -m speed -f "$PKG" >/dev/null

# Démarrage mesuré tout de suite après l'installation.
START="$("${ADB[@]}" shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$PKG/.HomeActivity")"
echo "etat=$(echo "$START" | sed -n 's/.*LaunchState: *\([A-Z]*\).*/\1/p' | tr -d '\r')"
echo "premier_affichage_ms=$(echo "$START" | sed -n 's/.*TotalTime: *\([0-9]*\).*/\1/p' | tr -d '\r')"

# Service de touches : après une mise à jour, la box ne le relance que si le réglage change.
SERVICE="$PKG/.remote.KeyRemapService"
CURRENT="$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')"
OTHERS="$(echo "$CURRENT" | tr ':' '\n' | grep -v "^$PKG/" | grep -v '^null$' | paste -sd: - || true)"
if [[ -n "$OTHERS" ]]; then
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$OTHERS"
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$OTHERS:$SERVICE"
else
  "${ADB[@]}" shell settings delete secure enabled_accessibility_services >/dev/null
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$SERVICE"
fi
"${ADB[@]}" shell settings put secure accessibility_enabled 1
echo "version=$("${ADB[@]}" shell dumpsys package "$PKG" | sed -n 's/.*versionName=\(.*\)/\1/p' | head -1 | tr -d '\r')"
