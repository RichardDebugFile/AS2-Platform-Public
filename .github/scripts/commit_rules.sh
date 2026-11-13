#!/bin/sh
# Reglas del mensaje de commit (las usan el hook .githooks/commit-msg y el CI):
#   1. La primera linea lleva la clave de Jira mas especifica despues del tipo:
#        tipo(ambito): AS2-NN descripcion        (el ambito es opcional)
#   2. El cuerpo termina con la historia o la tarea:  Refs: HU-XX (AS2-NN)  o  Refs: AS2-NN
#
# commit_problema <archivo con el mensaje>: imprime el problema y devuelve 1, o devuelve 0 si cumple.
# Las lineas de comentario (#) no cuentan: git las quita del mensaje.

SUBJECT_RE='^[a-z]+(\([^)]+\))?!?: AS2-[0-9]+[.:]? '
REFS_RE='^Refs: ((HU|AS2)-[0-9]+)'

commit_problema() {
    limpio=$(grep -v '^#' "$1")
    resumen=$(printf '%s\n' "$limpio" | sed -n '/[^[:space:]]/{p;q;}')
    if ! printf '%s\n' "$resumen" | grep -Eq "$SUBJECT_RE"; then
        echo "la primera linea no lleva la clave de Jira: '$resumen'"
        return 1
    fi
    if ! printf '%s\n' "$limpio" | grep -Eq "$REFS_RE"; then
        echo "falta la linea 'Refs: HU-XX (AS2-NN)' o 'Refs: AS2-NN' al final del cuerpo"
        return 1
    fi
    return 0
}
