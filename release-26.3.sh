# FHASIX Minecraft 26.3: build mod + loader, publish to the GitHub repo, then VERIFY the upload.
# Usage: bash release-26.3.sh 5.8          build + publish to GitHub (the real release)
#        bash release-26.3.sh 5.8 test     build ONLY, copy the jar to /sdcard/codetest, push nothing
set -eo pipefail
cd "$(dirname "$0")"
V="${1:-5.8}"; V="${V#v}"; N="$V"
JV=25
REPO="$HOME/fhasix-client.standalone-26.3"
URL="https://github.com/R4YTAX-devs/fhasix-client.standalone-26.3.git"
RAW="https://raw.githubusercontent.com/R4YTAX-devs/fhasix-client.standalone-26.3/main"
export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

# 0) Java + certificates (first run only), then use them
JH=$(ls -d "$PREFIX"/lib/jvm/java-$JV* 2>/dev/null | head -1 || true)
if [ ! -s "$HOME/.fhasix-truststore.p12" ] || [ -z "$JH" ] || [ ! -x "$JH/bin/java" ]; then
  bash ./termux-setup.sh "$JV"
  JH=$(ls -d "$PREFIX"/lib/jvm/java-$JV* | head -1)
fi
export JAVA_HOME="$JH"; export PATH="$JH/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$HOME/.fhasix-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"
chmod +x gradlew

# 0b) read the real class names out of the Minecraft jar (Time Changer hook, fhasix-api.txt)
bash ./resolve-api.sh || true

# 1) compile the mod
MV="$N.0.0"; [ "$(echo "$N" | tr -cd . | wc -c)" -ge 2 ] && MV="$N.0"
sed -i "s/^mod_version=.*/mod_version=$MV/" gradle.properties
if ! ./gradlew build --no-daemon 2>&1 | tee build-output.log; then
  echo; echo "===== COMPILE ERRORS (send me these lines) ====="
  grep -E "error:" -A2 build-output.log | head -120
  exit 1
fi
MOD=$(ls -t build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)

# test mode: stop here (nothing is pushed, the loader is not built)
if [ "${2:-}" = "test" ]; then
  mkdir -p /sdcard/codetest && cp "$MOD" /sdcard/codetest/
  echo; echo "TEST BUILD OK: copied $(basename "$MOD") to /sdcard/codetest - nothing was pushed to GitHub."
  exit 0
fi

# 2) compile the loader
./gradlew -p loader build --no-daemon 2>&1 | tee loader-build-output.log
LDR=$(ls -t loader/build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)
LDRSRC=$(ls -t loader/build/libs/*-sources.jar 2>/dev/null | head -1 || true)

# 3) get the GitHub repo
git config --global credential.helper store
[ -d "$REPO/.git" ] || git clone "$URL" "$REPO"
cd "$REPO" && git checkout -B main 2>/dev/null || true
git pull --rebase origin main 2>/dev/null || true
cd - >/dev/null

# 4) copy source + jars + version.json into the repo
tar --exclude=.git --exclude=build --exclude=.gradle --exclude=dist --exclude=loader-dist --exclude='*.log' --exclude=fhasix-api.txt -cf - . | tar -xf - -C "$REPO"
rm -rf "$REPO/dist" "$REPO/loader-dist"; mkdir -p "$REPO/dist" "$REPO/loader-dist"
cp "$MOD" "$REPO/dist/fhasix-client-$V.jar"
cp "$LDR" "$REPO/loader-dist/fhasix-loader.jar"
[ -n "$LDRSRC" ] && cp "$LDRSRC" "$REPO/loader-dist/fhasix-loader-sources.jar"
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
  else
    echo "WARNING: version.json is new but the jar on GitHub does not match yet - wait 5 minutes and check: $RAW/version.json"
  fi
else
  echo "WARNING: could not confirm on GitHub. Is the repo public? Check: $RAW/version.json"
fi
echo "Files on GitHub: dist/fhasix-client-$V.jar  loader-dist/fhasix-loader.jar"
