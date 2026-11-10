# Everyday tasks for Moneyment. Run `just` to list them.

export JAVA_HOME := `/usr/libexec/java_home -v 21`
export ANDROID_HOME := "/opt/homebrew/share/android-commandlinetools"

adb := ANDROID_HOME / "platform-tools/adb"
emulator := ANDROID_HOME / "emulator/emulator"
avd := "moneyment"
app := "eu.vieitesss.moneyment"

default:
    @just --list

# Run the unit tests.
test:
    ./gradlew testDebugUnitTest

# Start the emulator unless it is already running, and wait until it has booted.
emulator:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! {{adb}} devices | grep -q '^emulator-'; then
        nohup {{emulator}} -avd {{avd}} >/tmp/moneyment-emulator.log 2>&1 &
    fi
    {{adb}} wait-for-device
    until [[ "$({{adb}} shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 2; done

# Build the signed release APK, install it on the emulator over the current one (data kept), and open the app.
# Release, not debug: the two are signed with different keys and cannot replace each other.
update: emulator
    ./gradlew assembleRelease
    {{adb}} install -r app/build/outputs/apk/release/app-release.apk
    {{adb}} shell am start --display 0 -n {{app}}/.ui.MainActivity

# Stop the emulator.
emulator-stop:
    {{adb}} emu kill
