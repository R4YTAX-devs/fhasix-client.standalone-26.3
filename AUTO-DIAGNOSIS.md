# FHASIX local build diagnostics

The Java compiler is configured to report up to 1,000 diagnostics per compilation and more detailed messages. Use `./gradlew build --continue --stacktrace --no-daemon` to run locally and, in a shell with `tee`, save all output using `2>&1 | tee build-output.log`.

This is diagnostic automation, not an automatic code-repair bot. Compiler messages can cascade from a smaller number of root causes, and rendering/mixin API changes still need source-aware fixes and a successful build to verify them.

The current `NewPerf.java` import uses `net.minecraft.particle.ParticlesMode`, and both rendering files use `Camera.getCameraPos()`, as in the v4 source. This ZIP has not been built in this environment, so compilation is not verified.
