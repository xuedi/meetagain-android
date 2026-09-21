# meetagain-app - the daily loop. `just` lists the recipes.

export ANDROID_HOME := env("ANDROID_HOME", home_directory() / "Android/Sdk")

adb := ANDROID_HOME / "platform-tools/adb"
debug_id := "org.meetagain.app.debug"

default:
    @just --list

# Show what the build needs and whether this machine has it
doctor:
    -java -version
    @echo "ANDROID_HOME={{ ANDROID_HOME }}"
    @test -d "{{ ANDROID_HOME }}/platforms" || echo "missing: Android SDK platforms in ANDROID_HOME"
    -{{ adb }} version
    -{{ adb }} devices
    -{{ ANDROID_HOME }}/emulator/emulator -list-avds

# Start the meetagain emulator in the background
emulator:
    nohup {{ ANDROID_HOME }}/emulator/emulator -avd meetagain >/dev/null 2>&1 &

# Let the device reach a local MeetAgain at http://localhost:8000
reverse:
    {{ adb }} reverse tcp:8000 tcp:80

# Build and install the debug build; another server with -Pmeetagain.baseUrl=https://...
install *args:
    ./gradlew :app:installDebug {{ args }}

# Install and start the debug build, talking to the local MeetAgain
run *args: reverse (install args)
    {{ adb }} shell am start -n {{ debug_id }}/org.meetagain.app.MainActivity

# Follow the debug build's log
logcat:
    {{ adb }} logcat --pid=$({{ adb }} shell pidof -s {{ debug_id }})

# Run the JVM tests
test:
    ./gradlew :app:testDebugUnitTest

# Record the reference screenshots after an intended UI change
screenshots:
    ./gradlew :app:recordRoborazziDebug

# Everything CI checks: format, lint, tests and screenshots, no literals in the UI, the release APK guard
check:
    ./gradlew spotlessCheck :app:lintRelease :app:verifyRoborazziDebug :app:assembleRelease
    scripts/timed.sh "literals check" scripts/check-literals.sh
    scripts/timed.sh "dependency guard" scripts/check-dependencies.sh
    scripts/timed.sh "version check" scripts/check-version.sh
    @cat build/check-times.log

# Format the Kotlin sources
fix:
    ./gradlew spotlessApply

# Build the unsigned release APK
release:
    ./gradlew :app:assembleRelease
    @ls -l app/build/outputs/apk/release/*.apk

# Set the app version and the README badge together; then commit and push a vX.Y.Z tag to release it
version new:
    sed -i -E 's/^val appVersion = "[^"]+"$/val appVersion = "{{ new }}"/' app/build.gradle.kts
    sed -i -E 's#(img\.shields\.io/badge/Version-)[0-9]+\.[0-9]+\.[0-9]+-#\1{{ new }}-#' README.md
    scripts/check-version.sh

# Fetch the server's API description, the copy the contract test checks against
api-refresh:
    curl -fsS https://meetagain.org/api/openapi.json | python3 -m json.tool --indent 2 > api/openapi.json
