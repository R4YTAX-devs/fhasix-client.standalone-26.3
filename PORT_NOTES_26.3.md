# FHASIX Client + Loader 26.3 port
Ported from 1.21.11 (Yarn) to Minecraft 26.3 (Mojang names, Java 25, SDL input) WITHOUT a compiler,
so the first build may show compile errors: send build-output.log / loader-build-output.log.
Switched off for now: Block Overlay, Ore Highlight (need the new 26.3 renderer). Time Changer may not work.
Mixins use require=0: a changed target only disables that one feature.
Run: bash release-26.3.sh v5.2  (builds mod + loader, pushes to the 26.3 GitHub repo)

5.2.1: Camera.setup was renamed Camera.alignWithEntity in 26.x (free look hooks it now). Full Bright no longer depends on the
accessor mixin. On every start the mod writes fhasix-diag.txt (game folder, and /sdcard/Download if allowed) listing the real
method names of every class the mixins hook and a MISSING marker for each hook target that no longer exists.

5.8 (Motion Blur rebuilt for 26.3): 26.3 removed the single post-effect slot of the game renderer (the old code looked for it by
name and so the blur stayed off), and it compiles every shader with ShaderC on OpenGL and Vulkan (every in/out needs a
location). Now: the blur effect is put into the player's post-effect list (LocalPlayer#getActivePostEffects / setActivePostEffects,
the list /posteffect fills), the shaders follow the 26.3 rules (shaders/post/motion_blur.fsh, motion_copy.fsh), and the blend is
refreshed every frame from the real frame time through mixin/NewPostPassMixin. Everything that touches the GPU / post-effect
classes is looked up by name at run time; if a piece is missing the mod uses the five fixed-strength effects
(post_effect/motion_blur_1..5.json) and never crashes. fhasix-blur-diag.txt (game folder) says what was found.
Options: Blur strength + "Less blur under 40 FPS". 1.21.11 is unchanged.
