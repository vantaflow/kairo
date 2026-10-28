#!/usr/bin/env bash
# Publie une nouvelle version de Kairo sur GitHub (la box la propose dans Réglages › Mise à jour).
# Usage : scripts/release.sh <version>   ex. scripts/release.sh 0.3.0
#   KAIRO_SSH_PW doit contenir le mot de passe SSH du serveur de build (voir scripts/remote.py).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?Usage : scripts/release.sh <version, ex. 0.3.0>}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version invalide : $VERSION" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Arbre git non propre : commitez d'abord." >&2; exit 1; }

# versionCode = MAJ*10000 + MIN*100 + PATCH
IFS=. read -r MAJ MIN PAT <<<"$VERSION"
CODE=$((MAJ * 10000 + MIN * 100 + PAT))
sed -i -E "s/versionCode = [0-9]+/versionCode = $CODE/; s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" app/build.gradle.kts
git commit -qam "chore: version $VERSION"

# Build signé avec la clé du projet (la même pour toutes les versions : mise à jour possible).
scripts/remote_build.sh :app:assembleRelease
APK="build-remote/kairo-$VERSION.apk"
cp build-remote/app-release.apk "$APK"
# Nom fixe pour le lien permanent …/releases/latest/download/kairo.apk (Downloader, site).
cp build-remote/app-release.apk build-remote/kairo.apk

git push -q origin master
gh release create "v$VERSION" "$APK" build-remote/kairo.apk --repo vantaflow/kairo --title "Kairo $VERSION" --generate-notes
echo "Version $VERSION publiée."
