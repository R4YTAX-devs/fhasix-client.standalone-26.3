# FHASIX 26.3: build mod + loader, then publish everything to the 26.3 GitHub repo.
# Usage: bash release-26.3.sh v5.2
set -eo pipefail
cd "$(dirname "$0")"
V="${1:-v5.2}"; N="${V#v}"
REPO="$HOME/fhasix-client.standalone-26.3"
URL="https://github.com/R4YTAX-devs/fhasix-client.standalone-26.3.git"
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-25-openjdk}"

# 1) compile the mod
sed -i "s/^mod_version=.*/mod_version=$N.0.0/" gradle.properties
./gradlew build --no-daemon 2>&1 | tee build-output.log
MOD=$(ls -t build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)

# 2) compile the loader
./gradlew -p loader build --no-daemon 2>&1 | tee loader-build-output.log
LDR=$(ls -t loader/build/libs/*.jar | grep -v -e '-sources' -e '-dev' | head -1)

# 3) get the GitHub repo
git config --global credential.helper store
[ -d "$REPO/.git" ] || git clone "$URL" "$REPO"
cd "$REPO" && git checkout -B main 2>/dev/null || true
cd - >/dev/null

# 4) copy source + jars + version.json into the repo
tar --exclude=.git --exclude=build --exclude=.gradle --exclude=dist --exclude=loader-dist --exclude='*.log' -cf - . | tar -xf - -C "$REPO"
rm -rf "$REPO/dist" "$REPO/loader-dist"; mkdir -p "$REPO/dist" "$REPO/loader-dist"
cp "$MOD" "$REPO/dist/fhasix-client-$V.jar"
cp "$LDR" "$REPO/loader-dist/fhasix-loader.jar"
S=$(sha256sum "$REPO/dist/fhasix-client-$V.jar" | cut -d' ' -f1)
Z=$(stat -c%s "$REPO/dist/fhasix-client-$V.jar")
printf '{\n  "version": "%s",\n  "jar": "dist/fhasix-client-%s.jar",\n  "sha256": "%s",\n  "size": %s\n}\n' "$V" "$V" "$S" "$Z" > "$REPO/version.json"

# 5) test copies on the phone
mkdir -p /sdcard/codetest
cp "$MOD" "/sdcard/codetest/fhasixClient-V$N-26.3.jar"
cp "$LDR" /sdcard/codetest/fhasix-loader-26.3.jar

# 6) push
cd "$REPO" && git add -A && git commit -m "Release $V (Minecraft 26.3)" && git push -u origin main
echo "DONE: $V pushed to $URL"
