#!/usr/bin/env bash
# Compile sur le serveur de build puis installe Kairo sur la box par ADB, avec toutes les autorisations.
# Usage : scripts/deploy.sh <IP_BOX> [--no-build]
#   KAIRO_SSH_PW doit contenir le mot de passe SSH du serveur de build (sauf avec --no-build).
set -euo pipefail
cd "$(dirname "$0")/.."

BOX="${1:?Usage : scripts/deploy.sh <IP_BOX> [--no-build]}"
PKG=com.tvhome.launcher
# Version release : optimisée (R8), bien plus fluide que la debug sur la box.
APK=build-remote/app-release.apk

if [[ "${2:-}" != "--no-build" ]]; then
  echo "== Build sur le serveur"
  scripts/remote_build.sh :app:assembleRelease
fi
[[ -f "$APK" ]] || { echo "APK introuvable : $APK" >&2; exit 1; }

echo "== Connexion à la box $BOX"
adb connect "$BOX:5555" >/dev/null
timeout 30 adb -s "$BOX:5555" wait-for-device || { echo "Box $BOX injoignable (ADB réseau activé ? même réseau ?)" >&2; exit 1; }
ADB=(adb -s "$BOX:5555")

echo "== Installation"
"${ADB[@]}" install -r "$APK"
# Compilation à l'avance : démarrage et défilement plus fluides.
"${ADB[@]}" shell cmd package compile -m speed -f $PKG

echo "== Autorisations"
EPG=com.android.providers.tv.permission.READ_EPG_DATA
if "${ADB[@]}" shell dumpsys package $PKG | grep -q "$EPG: granted=true"; then
  echo "  EPG : déjà accordée"
else
  "${ADB[@]}" shell pm grant $PKG $EPG 2>/dev/null && echo "  EPG : accordée" || echo "  EPG : refusée par la box"
fi
# Indispensable pour lire le « À suivre » des autres applis (sinon le TvProvider ne renvoie que nos lignes).
"${ADB[@]}" shell pm grant $PKG android.permission.READ_TV_LISTINGS || true
"${ADB[@]}" shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true

# Service de touches : ajouté aux services déjà actifs, sans les écraser.
SERVICE="$PKG/.remote.KeyRemapService"
CURRENT=$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ "$CURRENT" != *"$SERVICE"* ]]; then
  if [[ -z "$CURRENT" || "$CURRENT" == "null" ]]; then NEW="$SERVICE"; else NEW="$CURRENT:$SERVICE"; fi
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$NEW"
fi
"${ADB[@]}" shell settings put secure accessibility_enabled 1

# Installation des mises à jour depuis GitHub (Réglages › Mise à jour).
"${ADB[@]}" shell appops set $PKG REQUEST_INSTALL_PACKAGES allow || true

# Écran de veille Kairo.
"${ADB[@]}" shell settings put secure screensaver_components "$PKG/.dream.TvHomeDream" || true
"${ADB[@]}" shell settings put secure screensaver_enabled 1 || true

echo "== Lancement"
"${ADB[@]}" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n "$PKG/.HomeActivity" >/dev/null
echo "Kairo installé et lancé sur $BOX."
