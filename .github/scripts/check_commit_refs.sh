#!/bin/sh
# Verifica que cada commit (sin contar las fusiones) entre <base> y <head> indique su historia o
# tarea de Jira: HU-XX o AS2-NN en el resumen o en el cuerpo. Es la misma regla del hook
# .githooks/commit-msg; el CI la aplica a los commits nuevos de cada pull request.
#
# Uso: sh .github/scripts/check_commit_refs.sh <base> <head>

base="$1"
head="$2"
if [ -z "$base" ] || [ -z "$head" ]; then
    echo "uso: $0 <base> <head>" >&2
    exit 2
fi

total=0
fallos=0
for c in $(git rev-list --no-merges "$base..$head"); do
    total=$((total + 1))
    if ! git log -1 --format=%B "$c" | grep -Eq '(HU|AS2)-[0-9]+'; then
        fallos=$((fallos + 1))
        echo "::error title=Commit sin historia::$(git log -1 --format='%h %s' "$c") no indica HU-XX ni AS2-NN"
    fi
done

if [ "$fallos" -gt 0 ]; then
    echo "$fallos de $total commits no indican su historia. Agrega 'Refs: HU-XX' (o AS2-NN) al mensaje,"
    echo "por ejemplo con 'git rebase -i' y 'reword', y vuelve a subir la rama."
    exit 1
fi
echo "$total commits revisados: todos indican su historia o tarea."
