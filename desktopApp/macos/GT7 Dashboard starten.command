#!/bin/sh
# Starts GT7 Dashboard with an installed JDK (17+) from Terminal.
#
# macOS blocks the local network for the app bundle ("No route to host" on the PS5 heartbeat)
# and never shows the permission prompt - with ad-hoc and self-signed signatures alike.
# A plain java process started from Terminal counts as Terminal's network access, which works.
APP="/Applications/GT7 Dashboard.app/Contents/app"
CFG="$APP/GT7 Dashboard.cfg"
LOG="$HOME/Library/Logs/GT7Dashboard.log"

if [ ! -f "$CFG" ]; then
    echo "GT7 Dashboard nicht gefunden: $APP"
    echo "Bitte die App zuerst nach „Programme“ ziehen."
    exit 1
fi
JAVA_HOME=$(/usr/libexec/java_home -v 17+ 2>/dev/null)
if [ -z "$JAVA_HOME" ]; then
    echo "Kein Java ab Version 17 gefunden. Bitte ein JDK installieren, z. B. von https://adoptium.net"
    exit 1
fi

# classpath from the app's launcher config, so jar names stay right after updates
CP=$(grep '^app.classpath=' "$CFG" | sed "s|^app.classpath=\$APPDIR|$APP|" | paste -sd: -)

mkdir -p "$HOME/Library/Logs"
nohup "$JAVA_HOME/bin/java" \
    "-Dskiko.library.path=$APP" \
    "-Dcompose.application.resources.dir=$APP/resources" \
    -Dcompose.application.configure.swing.globals=true \
    "-Xdock:name=GT7 Dashboard" \
    "-Xdock:icon=$APP/../Resources/GT7 Dashboard.icns" \
    -cp "$CP" de.stiller.gt7.desktop.MainKt >"$LOG" 2>&1 &
echo "GT7 Dashboard läuft (Java: $JAVA_HOME). Dieses Fenster kann geschlossen werden."
