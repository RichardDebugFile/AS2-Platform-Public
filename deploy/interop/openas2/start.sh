#!/bin/sh
# Arranque de OpenAS2 para la prueba (AS2-94): configuracion de la imagen + socios y certificados de
# prueba. Luego sigue el arranque normal de la imagen oficial.
set -eu
CONF=$OPENAS2_BASE/config
[ -f "$CONF/config.xml" ] || cp -a "$OPENAS2_BASE"/config_template/. "$CONF"/
cp /interop/partnerships.xml "$CONF/partnerships.xml"
cp /certs/openas2/as2_certs.p12 "$CONF/as2_certs.p12"
{
    echo "as2_keystore_password=$(cat /certs/secrets/openas2-password)"
    echo "storageBaseDir=/opt/openas2/data"
} > "$CONF/openas2.properties"
exec "$OPENAS2_BASE/bin/start-container.sh"
