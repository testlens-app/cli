<img width="1280" height="821" alt="1267842" src="https://github.com/user-attachments/assets/42d7f711-14f7-49b6-89e4-2cd98f92046c" /># TestLens CLI

[![CI](https://github.com/testlens-app/cli/actions/workflows/main.yml/badge.svg)](https://github.com/testlens-app/cli/actions/workflows/main.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

TestLens CLI — see https://testlens.app.

<a href="https://asciinema.org/a/1267842" target="_blank"><img width="1459" height="986" alt="testlens-cli" src="https://github.com/user-attachments/assets/2e9c6bc3-02d0-4483-ab6b-909fd1099001" /></a>

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
