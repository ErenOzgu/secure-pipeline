#!/usr/bin/env bash
# annotate-tail.sh TITLE FILE [LINES]
# Print the last lines of a log as one GitHub error annotation. A failed step then says why on the run page and
# in the API, without anyone having to open the raw log.
set -euo pipefail
title=${1:?title}
file=${2:?file}
lines=${3:-25}
body=$(tail -n "$lines" "$file" | sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}')
echo "::error title=${title}::${body}"
