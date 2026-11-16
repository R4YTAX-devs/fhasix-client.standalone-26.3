# FHASIX Client + Loader 26.3 port
Ported from 1.21.11 (Yarn) to Minecraft 26.3 (Mojang names, Java 25, SDL input) WITHOUT a compiler,
so the first build may show compile errors: send build-output.log / loader-build-output.log.
Switched off for now: Block Overlay, Ore Highlight (need the new 26.3 renderer). Time Changer may not work.
Mixins use require=0: a changed target only disables that one feature.
Run: bash release-26.3.sh v5.2  (builds mod + loader, pushes to the 26.3 GitHub repo)
