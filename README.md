# TestLens CLI

[![CI](https://github.com/testlens-app/cli/actions/workflows/main.yml/badge.svg)](https://github.com/testlens-app/cli/actions/workflows/main.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

TestLens CLI — see https://testlens.app.

Find the full documentation including installation instructions at https://testlens.app/docs/features/cli.

## Build from Source

Requirements:
- [GraalVM CE 25](https://www.graalvm.org), discoverable via [Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html) — easiest is to set it as `JAVA_HOME`

Alternatively, use the Nix dev shell by running `nix develop`.

Build the native binary:

```
./gradlew nativeCompile
```

The binary is written to `./build/native/nativeCompile`.
