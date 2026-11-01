# Changelog

## Unreleased

## 0.1.0 - 2026-11-01

First release.

### Added

- A small Java 21 service (`/healthz`, `/version`, `/hello`) with eight defensive response headers, 22 tests and a
  coverage floor of 80 % of lines.
- A GitHub Actions pipeline with six jobs: build and test, image build and smoke test, OSV-Scanner, SonarQube,
  OWASP ZAP baseline, and a schema check of the GitLab file.
- A "must fail" twin for each scanner: log4j-core 2.14.1 for OSV-Scanner, `alpine:3.12` for Trivy, code that is wrong
  on purpose for SonarQube, a server without defensive headers for ZAP.
- A SonarQube quality gate defined as code (`sonar/quality-gate.json`).
- Pinned actions (commit SHA), pinned images (digest) and `tools.lock` for downloaded tools, cross-checked against the
  publishers' checksum files.
- A GitLab CI version of the stages, validated against GitLab's schema and **not run on GitLab**.
- Dependabot for actions, Maven and Docker, monthly and grouped.
- A release workflow that releases from the first numbered heading of this file and attaches the jar and its
  `SHA256SUMS`.
