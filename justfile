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

# Build and install the debug build
install:
    ./gradlew :app:installDebug

# Install and start the debug build, talking to the local MeetAgain
run: reverse install
    {{ adb }} shell am start -n {{ debug_id }}/org.meetagain.app.MainActivity

# Follow the debug build's log
logcat:
    {{ adb }} logcat --pid=$({{ adb }} shell pidof -s {{ debug_id }})

# Run the JVM tests
test:
    ./gradlew :app:testDebugUnitTest

# Everything CI checks: format, lint, tests, the release build
check:
    ./gradlew spotlessCheck :app:lintRelease :app:testDebugUnitTest :app:assembleRelease

# Format the Kotlin sources
fix:
    ./gradlew spotlessApply

# Build the unsigned release APK
release:
    ./gradlew :app:assembleRelease
    @ls -l app/build/outputs/apk/release/*.apk
