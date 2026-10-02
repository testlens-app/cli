# Debugging the TUI

## Why the IntelliJ run dialog doesn't work

Starting the program from the IntelliJ run dialog forces `--mode=cli`, because
Gradle executes `java` without a TTY. The TUI needs a terminal, so bugs
cannot be reproduced that way. Instead, start the app in a real terminal with a
debugger agent attached, and connect IntelliJ to it.

## How to debug

Build the distribution, then start the launcher with the JDWP agent:

```sh
gradle installDist

JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" \
  build/install/testlens/bin/testlens pr <num> --repo <org>/<repo> --token $(gh auth token)
```

- suspend=y halts the JVM before main runs, so you can attach and set
  breakpoints before anything executes.
- The JVM listens on port 5005.

Then in IntelliJ: Run → Attach to Process (or a Remote JVM Debug run
configuration pointing at localhost:5005), and debug the TUI as usual.

## Moving from debuggin to testing

It's best to try to use debugging only to capture the response from the server that caused
the failure if possible. Once the shape of the problematic response is known, it should be
added as a new test case in `app.testlens.cli.tui`. Using tests, it's possible to debug
the TUI code.
