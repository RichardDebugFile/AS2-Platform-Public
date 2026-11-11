#!/bin/sh
# Certificados de prueba del entorno de interoperabilidad (AS2-94). Autofirmados, con nombres ficticios
# (*.example), generados en el volumen "certs" la primera vez; nunca se guardan en el repositorio.
#
#   /certs/plataforma/plataforma.p12   clave y certificado de la plataforma (as2-adapter)
#   /certs/plataforma/openas2.cer      certificado de OpenAS2 (cifrar y verificar su MDN)
#   /certs/openas2/as2_certs.p12       clave de OpenAS2 y certificado de la plataforma
#   /certs/secrets/...                 claves de los almacenes, aleatorias
set -eu
C=/certs
if [ -f "$C/openas2/as2_certs.p12" ]; then
    echo "Certificados de prueba ya generados en $C"
    exit 0
fi
mkdir -p "$C/plataforma" "$C/openas2" "$C/secrets/plataforma" "$C/tmp"

pass() { head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
P_PLAT=$(pass)
P_OAS2=$(pass)

# Plataforma: su clave en un PKCS#12 propio.
keytool -genkeypair -alias plataforma -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 730 \
    -dname "CN=plataforma.example, O=Plataforma AS2 (prueba), C=EC" \
    -storetype PKCS12 -keystore "$C/plataforma/plataforma.p12" -storepass "$P_PLAT" -keypass "$P_PLAT"
keytool -exportcert -rfc -alias plataforma -keystore "$C/plataforma/plataforma.p12" -storepass "$P_PLAT" \
    -file "$C/tmp/plataforma.cer"

# OpenAS2: su clave y, como certificado de confianza, el de la plataforma (para verificar su firma).
keytool -genkeypair -alias openas2 -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 730 \
    -dname "CN=openas2.example, O=Socio OpenAS2 (prueba), C=EC" \
    -storetype PKCS12 -keystore "$C/openas2/as2_certs.p12" -storepass "$P_OAS2" -keypass "$P_OAS2"
keytool -importcert -noprompt -alias plataforma -file "$C/tmp/plataforma.cer" \
    -storetype PKCS12 -keystore "$C/openas2/as2_certs.p12" -storepass "$P_OAS2"
keytool -exportcert -rfc -alias openas2 -keystore "$C/openas2/as2_certs.p12" -storepass "$P_OAS2" \
    -file "$C/plataforma/openas2.cer"

# El nombre del archivo es el de la propiedad de Spring (configtree): as2-adapter la lee sin variables.
printf '%s' "$P_PLAT" > "$C/secrets/plataforma/as2.spike.keystore-password"
printf '%s' "$P_OAS2" > "$C/secrets/openas2-password"
rm -rf "$C/tmp"
# Los contenedores corren con usuarios sin privilegios: deben poder leerlos.
chmod -R a+rX "$C"
echo "Certificados de prueba generados en $C"
