#!/bin/sh
# Envia un documento X12 a OpenAS2 a traves de la plataforma y muestra el resultado del MDN.
#   ./enviar.sh                       envia el 850 de ejemplo
#   ./enviar.sh ruta/al/archivo.edi   envia ese archivo
set -eu
cd "$(dirname "$0")"
FILE=${1:-../../documents-service/src/test/resources/x12/850-4010.edi}
NAME=$(basename "$FILE")
echo "Enviando $NAME a OpenAS2 (AS2-From PLATAFORMA, AS2-To OPENAS2)..."
curl -sS -X POST "http://localhost:8085/spike/send?fileName=$NAME" \
    -H "Content-Type: application/edi-x12" --data-binary "@$FILE"
echo
