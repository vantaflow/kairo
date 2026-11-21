#!/usr/bin/env bash
# Mesure la fluidité de l'accueil de Kairo sur une box, sans rien installer.
# Usage : bash scripts/mesure-accueil.sh <ip> [secondes]
# Affiche : jank_pct (part d'images tardives pendant la navigation) et pss_mo (mémoire de l'appli).
# Le premier affichage se mesure à part, juste après une installation par deploy.sh :
#   adb -s <ip>:5555 shell am start -W -n com.vantaflow.kairo/.HomeActivity   (valeur TotalTime)
set -euo pipefail

IP="${1:?usage: mesure-accueil.sh <ip> [secondes]}"
DUREE="${2:-30}"
PKG=com.vantaflow.kairo
ADB=(adb -s "$IP:5555")

"${ADB[@]}" shell input keyevent KEYCODE_HOME
sleep 2
"${ADB[@]}" shell dumpsys gfxinfo "$PKG" reset >/dev/null

# Parcours fixe : descendre dans les rangées, les parcourir, remonter, revenir.
SEQUENCE=(20 20 20 20 22 22 22 22 22 22 19 19 19 19 21 21 21 21 21 21)
FIN=$(( $(date +%s) + DUREE ))
i=0
while [ "$(date +%s)" -lt "$FIN" ]; do
  "${ADB[@]}" shell input keyevent "${SEQUENCE[$(( i % ${#SEQUENCE[@]} ))]}"
  i=$(( i + 1 ))
  sleep 0.4
done

GFX="$("${ADB[@]}" shell dumpsys gfxinfo "$PKG")"
MEM="$("${ADB[@]}" shell dumpsys meminfo "$PKG")"
"${ADB[@]}" shell input keyevent KEYCODE_HOME

echo "touches=$i"
echo "images=$(echo "$GFX" | sed -n 's/.*Total frames rendered: *\([0-9]*\).*/\1/p' | head -1)"
echo "jank_pct=$(echo "$GFX" | sed -n 's/.*Janky frames: *[0-9]* *(\([0-9.]*\)%).*/\1/p' | head -1)"
PSS_KO="$(echo "$MEM" | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' | head -1)"
echo "pss_mo=$(( ${PSS_KO:-0} / 1024 ))"
