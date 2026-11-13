#!/bin/sh
# Verifica que cada commit (sin contar las fusiones) entre <base> y <head> cumpla las reglas de
# .github/scripts/commit_rules.sh: clave de Jira en la primera linea (`tipo(ambito): AS2-NN ...`) y
# `Refs: HU-XX (AS2-NN)` o `Refs: AS2-NN` en el cuerpo. Es la misma regla del hook .githooks/commit-msg;
# el CI la aplica a los commits nuevos de cada pull request.
#
# Uso: sh .github/scripts/check_commit_refs.sh <base> <head>

base="$1"
head="$2"
if [ -z "$base" ] || [ -z "$head" ]; then
    echo "uso: $0 <base> <head>" >&2
    exit 2
fi

. "$(dirname "$0")/commit_rules.sh"

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
total=0
fallos=0
for c in $(git rev-list --no-merges "$base..$head"); do
    total=$((total + 1))
    git log -1 --format=%B "$c" > "$tmp"
    if ! problema=$(commit_problema "$tmp"); then
        fallos=$((fallos + 1))
        echo "::error title=Commit sin su clave de Jira::$(git log -1 --format='%h' "$c"): $problema"
    fi
done

if [ "$fallos" -gt 0 ]; then
    echo "$fallos de $total commits no cumplen el formato 'tipo(ambito): AS2-NN descripcion' con"
    echo "'Refs: HU-XX (AS2-NN)' al final. Corrigelos (git rebase -i, reword) y vuelve a subir la rama."
    exit 1
fi
echo "$total commits revisados: todos llevan su clave de Jira y su referencia."
