# FHASIX local build (no GitHub Actions required)

This ZIP is based on the current v4 source and removes GitHub Actions files and GitHub-only instructions.

## Requirements

- Java 21 (the project targets Minecraft 1.21.11)
- Network access for the first Gradle/dependency download
- Android Termux: `pkg install openjdk-21 curl unzip coreutils`

## Android Termux / Linux / macOS

Optional structural check before compiling: `./preflight.sh` (requires Python 3).

Open a terminal in the extracted `FHASIX-standalone` directory and run:

```sh
chmod +x ./gradlew
./gradlew --version
./gradlew build --continue --stacktrace --no-daemon 2>&1 | tee build-output.log
```

The first `./gradlew` run downloads the official Gradle 9.2.1 distribution (about 130 MB) and verifies its SHA-256 checksum. Later runs reuse the cached copy. The build will also download Minecraft/Fabric dependencies, so the first full build may use additional data.

If the terminal reports that `tee` is missing, run `./gradlew build --continue --stacktrace --no-daemon` without the pipe. The build result will still appear in the terminal.

## Windows

Run `gradlew.bat build --continue --stacktrace --no-daemon` from this directory. The bootstrap downloads Gradle and checks its SHA-256 checksum.

## What the compiler reports

`build.gradle` configures Java compilation to report up to 1,000 compiler diagnostics in one invocation. This is a reporting cap, not a count or prediction of bugs. Several errors can come from one underlying issue. `--continue` lets Gradle attempt independent tasks, but compilation cannot continue past a source file's own fatal compiler errors.

## Important

This source has not been confirmed to compile successfully. The local wrapper only automates obtaining/running Gradle; it does not automatically repair source code. If the build fails, send `build-output.log` or the full terminal output so the actual remaining errors can be diagnosed.
