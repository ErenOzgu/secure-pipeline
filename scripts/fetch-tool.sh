#!/usr/bin/env bash
# Download a tool listed in tools.lock, check its SHA-256 against the lock, and put it in tools/bin/.
#
# tools.lock has one line per tool:  name  sha256  kind  url
# kind is "binary" (the download is the program) or "tar.gz:MEMBER" (the program is MEMBER inside the archive).
# A download whose hash differs from the lock is refused, and the error names the hash it actually got, so that
# updating a tool is a reviewed change to tools.lock and never a silent one.
set -euo pipefail

name=${1:?usage: fetch-tool.sh NAME}
root=$(cd "$(dirname "$0")/.." && pwd)

line=$(grep -E "^${name}[[:space:]]" "$root/tools.lock" || true)
if [[ -z "$line" ]]; then
  echo "::error title=unknown tool::${name} is not in tools.lock"
  exit 2
fi
read -r _ want kind url <<<"$line"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl --fail --silent --show-error --location --retry 3 --output "$tmp/download" "$url"
got=$(sha256sum "$tmp/download" | cut -d' ' -f1)
if [[ "$got" != "$want" ]]; then
  echo "::error title=${name} does not match tools.lock::lock says ${want}, the download is ${got} (${url})"
  exit 1
fi

mkdir -p "$root/tools/bin"
case "$kind" in
  binary)
    install -m 0755 "$tmp/download" "$root/tools/bin/$name"
    ;;
  tar.gz:*)
    member=${kind#tar.gz:}
    tar -xzf "$tmp/download" -C "$tmp" "$member"
    install -m 0755 "$tmp/$member" "$root/tools/bin/$name"
    ;;
  *)
    echo "::error title=bad tools.lock::unknown kind '${kind}' for ${name}"
    exit 2
    ;;
esac
echo "${name}: ${got} ok"
