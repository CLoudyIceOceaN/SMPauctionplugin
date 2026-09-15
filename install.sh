#!/bin/bash
# SMPauctionplugin one-command installer.
# Run this in your SERVER folder (the folder with the server jar in it):
#   curl -sL https://cloudyiceocean.github.io/SMPauctionplugin/install.sh | bash
set -e

AH_URL="https://cloudyiceocean.github.io/SMPauctionplugin/SMPauctionplugin.jar"
MONEY_URL="https://cloudyiceocean.github.io/SMPmoneyplugin/SMPmoneyplugin.jar"

if [ -d "plugins" ]; then
  DEST="plugins"
elif [ -d "../plugins" ]; then
  DEST="../plugins"
elif ls *.jar >/dev/null 2>&1 || [ -f "server.properties" ]; then
  mkdir -p plugins
  DEST="plugins"
else
  echo "Hmm, I can't find your server here."
  echo "cd into your server folder (the one with the server jar or"
  echo "server.properties in it), then run this command again."
  exit 1
fi

echo "Downloading SMPauctionplugin..."
curl -sL "$AH_URL" -o "$DEST/SMPauctionplugin.jar"

# The auction house needs the money plugin — grab it too if it's missing
if [ ! -f "$DEST/SMPmoneyplugin.jar" ]; then
  echo "Downloading SMPmoneyplugin (the auction house needs it)..."
  curl -sL "$MONEY_URL" -o "$DEST/SMPmoneyplugin.jar"
fi

echo ""
echo "  Installed to $DEST/"
echo ""
echo "  Now restart your server and you're done!"
echo "  In game: /ah opens the auction house, /sell opens the sell menu."
