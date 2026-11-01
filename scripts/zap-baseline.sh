#!/usr/bin/env bash
# zap-baseline.sh TARGET_URL NAME
# Run ZAP's baseline (passive) scan against a container on the docker network "scan". Needs ZAP_IMAGE in the
# environment. Writes zap-out/NAME.log, NAME.json, NAME.html and NAME-findings.log (only the WARN and FAIL lines),
# and exits with ZAP's own status: 0 nothing to report, 1 a failure, 2 warnings.
set -uo pipefail

target=${1:?usage: zap-baseline.sh TARGET_URL NAME}
name=${2:?usage: zap-baseline.sh TARGET_URL NAME}
out=zap-out

mkdir -p "$out"
chmod 777 "$out"
cp "$(dirname "$0")/../scan/zap-baseline.conf" "$out/baseline.conf"

status=0
docker run --rm --network scan --volume "${PWD}/${out}:/zap/wrk:rw" "${ZAP_IMAGE:?set ZAP_IMAGE}" \
  zap-baseline.py -t "$target" -c baseline.conf -J "${name}.json" -r "${name}.html" -m 1 2>&1 \
  | tee "${out}/${name}.log" || status=${PIPESTATUS[0]}

grep -E '^(WARN|FAIL)' "${out}/${name}.log" > "${out}/${name}-findings.log" || true
echo "zap exit status for ${name}: ${status}"
exit "$status"
