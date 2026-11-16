# FHASIX Client 26.3 (Minecraft 26.3, Fabric) - full source, version 5.2.2

Contents
- `src/`   the client mod (Java 25, Fabric Loom, Fabric API 0.166.2+26.3)
- `loader/` the FHASIX loader (checks GitHub for updates and installs them)
- `release-26.3.sh` builds both jars and publishes them to the GitHub repo (needs your own repo + token)

Build the jars on any computer with JDK 25:
    ./gradlew build                 -> build/libs/fhasix-client-*.jar
    ./gradlew -p loader build       -> loader/build/libs/fhasix-loader-*.jar
The `-sources.jar` next to each jar is produced automatically.

On start the mod writes `fhasix-diag.txt` (game folder) listing the real Minecraft method names its hooks rely on.
