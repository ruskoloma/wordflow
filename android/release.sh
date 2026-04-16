#!/bin/bash
# Quick release script for WordFlow
# Usage: ./release.sh 1.1.0 "Added new features"

set -e

VERSION=${1:?"Usage: ./release.sh <version> [description]"}
DESCRIPTION=${2:-"WordFlow v$VERSION"}

# Update version in build.gradle.kts
sed -i '' "s/versionName = \".*\"/versionName = \"$VERSION\"/" app/build.gradle.kts

# Bump versionCode
CURRENT_CODE=$(grep 'versionCode' app/build.gradle.kts | grep -o '[0-9]*')
NEW_CODE=$((CURRENT_CODE + 1))
sed -i '' "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts

echo "Updated to version $VERSION (code $NEW_CODE)"

# Commit, tag, push
git add -A
git commit -m "Release v$VERSION"
git tag "v$VERSION"
git push origin main
git push origin "v$VERSION"

echo ""
echo "Pushed tag v$VERSION — GitHub Actions will build and create the release."
echo "Or create a manual release:"
echo "  gh release create v$VERSION --title \"WordFlow v$VERSION\" --notes \"$DESCRIPTION\" app/build/outputs/apk/release/app-release.apk"
