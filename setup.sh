#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$HOME/projects/apcctv/TvControlCenter"

# ----------------------------------------------------------------------
# 0. Packages + GitHub auth
# ----------------------------------------------------------------------
pkg install -y git gh

if ! gh auth status >/dev/null 2>&1; then
    echo ">> GitHub login required (choose: GitHub.com -> HTTPS -> Login with browser)"
    gh auth login
fi

GH_USER=$(gh api user --jq '.login')
git config --global user.name  "$(gh api user --jq '.name // .login')" 2>/dev/null || true
git config --global user.email "${GH_USER}@users.noreply.github.com"    2>/dev/null || true

# ----------------------------------------------------------------------
# 1. .gitignore
# ----------------------------------------------------------------------
cat > .gitignore << 'EOF'
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
app/build/
captures/
.externalNativeBuild/
.cxx/
EOF

# ----------------------------------------------------------------------
# 2. README.md
# ----------------------------------------------------------------------
cat > README.md << 'EOF'
# TvControlCenter

An Android TV Control Center with the look, feel, and behavior of the
Apple TV (tvOS) Control Center.

## Features
- Translucent rounded panel that slides in from the right, dimmed scrim
- tvOS-style focus: white pill highlight, dark glyphs, springy scale animation
- User profile row, Sleep row, Now Playing card
- Quick toggles: Wi-Fi, Bluetooth, Focus (Do Not Disturb), Airplane,
  Screensaver, Power
- Live clock + date in the panel footer
- Full D-pad navigation: RIGHT on the rightmost item closes the panel,
  BACK or MENU closes it too

## Build
Every push triggers GitHub Actions, which builds `app-debug.apk`
(see the Actions tab -> latest run -> Artifacts).

Local build (desktop): `./gradlew assembleDebug` (JDK 17, Android SDK 34).

## Install on a TV
