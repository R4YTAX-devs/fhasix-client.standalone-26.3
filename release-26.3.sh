# FHASIX 26.3: build mod + loader, publish to the 26.3 GitHub repo, then VERIFY the upload.
# Usage: bash release-26.3.sh v5.2.1
set -eo pipefail
cd "$(dirname "$0")"
V="${1:-v5.2.1}"; N="${V#v}"
REPO="$HOME/fhasix-client.standalone-26.3"
URL="https://github.com/R4YTAX-devs/fhasix-client.standalone-26.3.git"
RAW="https://raw.githubusercontent.com/R4YTAX-devs/fhasix-client.standalone-26.3/main"
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-25-openjdk}"

# 1) compile the mod (also produces the -sources.jar)
sed -i "s/^mod_version=.*/mod_version=$N.0.0/" gradle.properties
./gradlew build --no-daemon 2>&1 | tee build-output.log
MOD=$(ls -t build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)
MODSRC=$(ls -t build/libs/*-sources.jar 2>/dev/null | head -1 || true)

# 2) compile the loader
./gradlew -p loader build --no-daemon 2>&1 | tee loader-build-output.log
LDR=$(ls -t loader/build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)

# 3) get the GitHub repo
git config --global credential.helper store
[ -d "$REPO/.git" ] || git clone "$URL" "$REPO"
cd "$REPO" && git checkout -B main 2>/dev/null || true
git pull --rebase origin main 2>/dev/null || true
cd - >/dev/null

# 4) copy source + jars + version.json into the repo (nothing is copied to /sdcard/codetest)
tar --exclude=.git --exclude=build --exclude=.gradle --exclude=dist --exclude=loader-dist --exclude='*.log' -cf - . | tar -xf - -C "$REPO"
rm -rf "$REPO/dist" "$REPO/loader-dist"; mkdir -p "$REPO/dist" "$REPO/loader-dist"
cp "$MOD" "$REPO/dist/fhasix-client-$V.jar"
[ -n "$MODSRC" ] && cp "$MODSRC" "$REPO/dist/fhasix-client-$V-sources.jar"
cp "$LDR" "$REPO/loader-dist/fhasix-loader.jar"
jar cf "$REPO/loader-dist/fhasix-loader-sources.jar" -C loader src -C loader build.gradle -C loader settings.gradle -C loader gradle.properties 2>/dev/null || true
S=$(sha256sum "$REPO/dist/fhasix-client-$V.jar" | cut -d' ' -f1)
Z=$(stat -c%s "$REPO/dist/fhasix-client-$V.jar")
printf '{\n  "version": "%s",\n  "jar": "dist/fhasix-client-%s.jar",\n  "sha256": "%s",\n  "size": %s\n}\n' "$V" "$V" "$S" "$Z" > "$REPO/version.json"

# 5) push
cd "$REPO" && git add -A && git commit -m "Release $V (Minecraft 26.3)" && git push -u origin main
echo "PUSHED: $V"

# 6) verify GitHub really serves the new release (the loader reads exactly these files)
ok=0
for i in 1 2 3 4 5 6 7 8; do
  RV=$(curl -fsSL "$RAW/version.json?t=$(date +%s)" 2>/dev/null | sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' || true)
  [ "$RV" = "$V" ] && { ok=1; break; }
  echo "  waiting for GitHub to publish ($i/8) - it currently serves: ${RV:-nothing}"; sleep 10
done
if [ "$ok" = 1 ]; then
  RS=$(curl -fsSL "$RAW/dist/fhasix-client-$V.jar?t=$(date +%s)" | sha256sum | cut -d' ' -f1)
  if [ "$RS" = "$S" ]; then
    echo "VERIFIED: GitHub serves version $V and the jar matches (sha256 $S)."
    echo "Loader (needs the OLD jar installed to see it): update will be offered because the sha256 differs."
  else
    echo "WARNING: version.json is new but the jar on GitHub does not match yet - wait 5 minutes and re-run: curl -fsSL $RAW/version.json"
  fi
else
  echo "WARNING: could not confirm on GitHub. Is the repo public? Check: $RAW/version.json"
fi
echo "Files on GitHub: dist/fhasix-client-$V.jar  dist/fhasix-client-$V-sources.jar  loader-dist/fhasix-loader.jar  loader-dist/fhasix-loader-sources.jar"
