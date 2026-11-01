#!/usr/bin/env bash
# Start a throw-away SonarQube, wait until it is up, and make a token for this run.
#
#   SONARQUBE_IMAGE=... scripts/sonarqube.sh up       starts it; SONAR_TOKEN is added to the job's environment
#   scripts/sonarqube.sh gate PROJECT_KEY             prints the quality gate of a project, one line per condition;
#                                                     exit status 0 when the gate is OK, 1 when it is not
#
# The server lives for the length of one job and is never reachable from outside it. The admin password is made
# up for the run and masked in the log.
set -euo pipefail

url=http://127.0.0.1:9000

case "${1:-}" in
  up)
    image=${SONARQUBE_IMAGE:?set SONARQUBE_IMAGE}
    sudo sysctl -w vm.max_map_count=524288 > /dev/null
    sudo sysctl -w fs.file-max=131072 > /dev/null
    docker run --detach --name sonarqube --publish 127.0.0.1:9000:9000 \
      --env SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true "$image" > /dev/null
    echo "image: $(docker inspect --format '{{index .RepoDigests 0}}' "$image" 2>/dev/null || echo "$image")"

    status=""
    for _ in $(seq 1 90); do
      status=$(curl --silent "$url/api/system/status" | jq -r '.status' 2>/dev/null || true)
      [[ "$status" == "UP" ]] && break
      sleep 4
    done
    if [[ "$status" != "UP" ]]; then
      echo "::error title=SonarQube did not start::status is '${status}'"
      docker logs sonarqube | tail -n 40
      exit 1
    fi
    echo "SonarQube $(curl --silent "$url/api/system/status" | jq -r '.version') is up"

    password=$(openssl rand -hex 16)
    echo "::add-mask::${password}"
    curl --fail --silent --show-error --user admin:admin --request POST "$url/api/users/change_password" \
      --data-urlencode login=admin --data-urlencode previousPassword=admin --data-urlencode "password=${password}"
    token=$(curl --fail --silent --show-error --user "admin:${password}" --request POST \
      "$url/api/user_tokens/generate" --data-urlencode name=pipeline | jq -r '.token')
    echo "::add-mask::${token}"
    echo "SONAR_TOKEN=${token}" >> "$GITHUB_ENV"
    ;;
  gate)
    key=${2:?usage: sonarqube.sh gate PROJECT_KEY}
    json=$(curl --fail --silent --show-error --user "${SONAR_TOKEN:?}:" \
      "$url/api/qualitygates/project_status?projectKey=${key}")
    state=$(jq -r '.projectStatus.status' <<<"$json")
    echo "quality gate of ${key}: ${state}"
    jq -r '.projectStatus.conditions[] | "  \(.status)\t\(.metricKey)\tactual=\(.actualValue // "-")\tlimit \(.comparator) \(.errorThreshold)"' <<<"$json"
    [[ "$state" == "OK" ]]
    ;;
  *)
    echo "usage: sonarqube.sh up | gate PROJECT_KEY" >&2
    exit 2
    ;;
esac
