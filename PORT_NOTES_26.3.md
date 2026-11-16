# FHASIX Client + Loader 26.3 port
Ported from 1.21.11 (Yarn) to Minecraft 26.3 (Mojang names, Java 25, SDL input) WITHOUT a compiler,
so the first build may show compile errors: send build-output.log / loader-build-output.log.
Switched off for now: Block Overlay, Ore Highlight (need the new 26.3 renderer). Time Changer may not work.
Mixins use require=0: a changed target only disables that one feature.
Run: bash release-26.3.sh v5.2  (builds mod + loader, pushes to the 26.3 GitHub repo)

5.2.1: Camera.setup was renamed Camera.alignWithEntity in 26.x (free look hooks it now). Full Bright no longer depends on the
accessor mixin. On every start the mod writes fhasix-diag.txt (game folder, and /sdcard/Download if allowed) listing the real
method names of every class the mixins hook and a MISSING marker for each hook target that no longer exists.
