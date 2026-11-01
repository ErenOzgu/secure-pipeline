#!/usr/bin/env bash
# For every tool in tools.lock that names the publisher's checksum file, check that the publisher says the same
# thing the lock does. The lock is what the pipeline trusts; this is how a wrong line in it gets noticed.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failed=0

while read -r name want _kind url sums; do
  [[ -z "${name:-}" || "$name" == \#* ]] && continue
  if [[ -z "${sums:-}" ]]; then
    echo "${name}: no publisher checksum file listed, not cross-checked"
    continue
  fi
  curl --fail --silent --show-error --location --retry 3 --output "$tmp/sums" "$sums"
  asset=$(basename "$url")
  # Lines look like "<sha256>  <file>" or "<sha256> *<file>"; some publishers prefix the file with ./
  published=$(awk -v f="$asset" '{ n=$2; sub(/^\*/, "", n); sub(/^\.\//, "", n); if (n == f) print $1 }' "$tmp/sums" | head -1)
  if [[ -z "$published" ]]; then
    echo "::error title=${name}::${asset} is not in the publisher's checksum file"
    failed=1
  elif [[ "$published" != "$want" ]]; then
    echo "::error title=${name}::tools.lock says ${want}, the publisher says ${published}"
    failed=1
  else
    echo "${name}: the publisher's checksum file agrees with tools.lock"
  fi
done < "$root/tools.lock"

exit "$failed"
