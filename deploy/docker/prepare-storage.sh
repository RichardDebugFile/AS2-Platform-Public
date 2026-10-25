#!/usr/bin/env bash
# Prepara el almacenamiento de las carpetas de los socios (HU-09), una vez por entorno y despues de
# "docker compose build":
#   - volumen as2data -> /opt/as2_data/partners (raiz de las carpetas de cada socio);
#   - recurso compartido del ERP -> /mnt/erp (en local, ./shared; en un servidor, ERP_SHARED_PATH en
#     .env con la ruta donde esta montado el recurso Samba/CIFS).
# Deja ambos con dueno el usuario sin privilegios de los contenedores. No borra ni mueve nada: se
# puede repetir sin riesgo.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
shared="${ERP_SHARED_PATH:-./shared}"

if [ ! -d "$shared" ]; then
    if [ "$shared" = "./shared" ]; then
        mkdir -p "$shared"
        echo "Creada la carpeta local del recurso compartido: $shared"
    else
        echo "ERP_SHARED_PATH=$shared no existe: monte primero el recurso compartido del ERP." >&2
        exit 1
    fi
fi

# Dentro del contenedor y como root, para que valga igual en Linux, macOS y Windows (Docker Desktop).
docker compose run --rm --no-deps --user root --entrypoint sh partners-service -c '
    set -e
    mkdir -p /opt/as2_data/partners
    chown app:app /opt/as2_data /opt/as2_data/partners
    chmod 0750 /opt/as2_data/partners
    if chown app:app /mnt/erp 2>/dev/null && chmod 0770 /mnt/erp 2>/dev/null; then
        echo "Recurso compartido listo en /mnt/erp"
    else
        echo "AVISO: no se pudo cambiar el dueno de /mnt/erp (normal en un montaje CIFS):"
        echo "       el montaje debe permitir escritura al usuario de los contenedores (uid $(id -u app))."
    fi
    su app -s /bin/sh -c "test -w /opt/as2_data/partners && test -w /mnt/erp" \
        && echo "Almacenamiento listo: el servicio puede escribir en ambas carpetas." \
        || { echo "El servicio no puede escribir en /opt/as2_data/partners o /mnt/erp." >&2; exit 1; }
'
