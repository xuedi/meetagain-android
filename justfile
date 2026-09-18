# meetagain-app - the recipes grow with the toolchain decided in the vision's foundation phase.

default:
    @just --list

# Show which mobile toolchain pieces are installed on this machine
doctor:
    -flutter --version
    -flutter doctor
    -java -version
    -adb version
