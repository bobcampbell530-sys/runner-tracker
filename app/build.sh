#!/bin/bash
# Build script for Runner Tracker Android app
# Requires: Android SDK command-line tools + Gradle 8.5+

set -e

echo "==================================="
echo "Runner Tracker - Build Script"
echo "==================================="

# Check for Gradle wrapper
if [ ! -f "../gradlew" ]; then
    echo "⚠ Gradle wrapper not found (expected ../gradlew)"
    echo ""
    echo "To build the APK:"
    echo "1. cd runner-tracker"
    echo "2. ./gradlew assembleDebug"
    echo ""
    echo "Or open in Android Studio → File → Open → select this folder"
    exit 1
fi

# Build the APK
echo "Building APK..."
cd ..
./gradlew assembleDebug

echo ""
echo "==================================="
echo "Build complete!"
echo "==================================="
echo ""
echo "APK location:"
echo "  app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on your phone:"
echo "1. Copy the APK to your phone (USB, email, etc.)"
echo "2. Tap the APK to install"
echo "3. Allow 'Install from unknown sources' if prompted"
echo "4. Open the app and enter your bib number + server URL"
