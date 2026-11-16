#!/usr/bin/env bash
# Looks inside the Minecraft jar that Gradle already downloaded and
#  1) points the Time Changer hook at the REAL class name
#  2) writes fhasix-api.txt (real class/method names for fog, block outline, clock, render events)
# Safe to run any time; if the jar is not downloaded yet it just does nothing.
cd "$(dirname "$0")"
OUT="$PWD/fhasix-api.txt"
MIX="src/main/java/com/fhasix/uitheme/mixin/NewClockMixin.java"
JAR=""
while IFS= read -r j; do
  if unzip -Z1 "$j" 2>/dev/null | grep -qx 'net/minecraft/client/Minecraft.class'; then JAR="$j"; break; fi
done < <(find "$HOME/.gradle" -type f -name '*.jar' -size +10M 2>/dev/null)
if [ -z "$JAR" ]; then echo "[resolve-api] Minecraft jar not downloaded yet - skipping (run the release once more after this build)"; exit 0; fi
echo "[resolve-api] Minecraft jar: $JAR"
LIST=$(unzip -Z1 "$JAR" 2>/dev/null)
fq(){ echo "$LIST" | grep -E "(^|/)$1\.class$" | head -1 | sed 's/\.class$//; s#/#.#g'; }

CLOCK=$(fq ClientClockManager)
if [ -n "$CLOCK" ]; then
  sed -i "s#net\.minecraft\.client\.RESOLVE_ME_ClientClockManager#$CLOCK#" "$MIX"
  if [ "$(grep -c "\"$CLOCK\"" "$MIX")" -gt 1 ]; then sed -i "0,/\"$CLOCK\",/{//d}" "$MIX"; fi   # no duplicate targets
  M=$(javap -p -cp "$JAR" "$CLOCK" 2>/dev/null | grep -oE '[A-Za-z]*TotalTicks[A-Za-z]*' | head -1)
  [ -n "$M" ] && [ "$M" != "getTotalTicks" ] && sed -i "s#method = \"getTotalTicks\"#method = \"$M\"#" "$MIX"
  echo "[resolve-api] clock hook -> $CLOCK#${M:-getTotalTicks}"
else
  echo "[resolve-api] ClientClockManager class not found in this Minecraft jar"
fi

{
  echo "FHASIX API DUMP  jar=$JAR"
  echo; echo "=== class names that matter"
  echo "$LIST" | grep -E '\.class$' | grep -E 'Fog|Outline|Highlight|ClockManager|Lightmap|LightTexture|Sky|Gizmo|LevelRenderer|BlockHit|SubmitNode|FeatureRenderDispatcher' | grep -v 'sounds/' | head -150
  for pat in ClientClockManager FogRenderer FogData FogParameters LevelRenderer GameRenderer Camera LightTexture Lightmap SkyRenderer BlockOutlineRenderer Gizmos; do
    C=$(fq "$pat"); [ -z "$C" ] && continue
    echo; echo "=== javap $C"; javap -p -cp "$JAR" "$C" 2>/dev/null | head -120
  done
  for p in $(echo "$LIST" | grep -E '\.class$' | grep -E '(Fog|Outline)[A-Za-z]*\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' | head -10); do
    echo; echo "=== javap $p"; javap -p -cp "$JAR" "$p" 2>/dev/null | head -80
  done
  FR=$(find "$HOME/.gradle" -name 'fabric-rendering-v1*.jar' ! -name '*sources*' 2>/dev/null | head -1)
  if [ -n "$FR" ]; then
    echo; echo "=== fabric rendering jar: $FR"
    unzip -Z1 "$FR" | grep 'api/client/rendering/v1/' | grep '\.class$' | head -80
    for c in $(unzip -Z1 "$FR" | grep -E 'api/client/rendering/v1/.*(Level|World)[A-Za-z]*(Events|Context)[A-Za-z]*\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' | head -8); do
      echo; echo "=== javap $c"; javap -p -cp "$FR:$JAR" "$c" 2>/dev/null | head -80
    done
  fi
} > "$OUT" 2>&1
cp -f "$OUT" /sdcard/Download/fhasix-api.txt 2>/dev/null && echo "[resolve-api] wrote /sdcard/Download/fhasix-api.txt ($(wc -l < "$OUT") lines)"
exit 0
