# Pre-build audit

The included structural preflight script is `tools/preflight.py`, runnable with `./preflight.sh` where Python 3 is installed. It checks source package/path consistency, Java brace/comment/string structure, internal imports, JSON syntax, mixin source references, and required Gradle version properties.

These checks are useful but are not a Java compiler and cannot verify Minecraft/Fabric method signatures. A full Gradle build is still required.
