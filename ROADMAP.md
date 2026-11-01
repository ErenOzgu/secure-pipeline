# Roadmap

Small steps, in order. A step is done when the pipeline is green, the README says what changed and the CHANGELOG has a
line for it. A step that adds a gate also gets an input that is wrong on purpose, and a job that requires the gate to
fail on it.

- [ ] Run the GitLab version on GitLab, read the image digests from a registry, pin them, and say in the README what
      changed between the schema-valid file and the one that ran
- [ ] A scheduled run (weekly) of the pipeline, so a vulnerability published after the last push still turns it red
- [ ] Secrets scanning of the history, with a commit holding a fake key that must be found
- [ ] hadolint on the Dockerfile, with a Dockerfile that is wrong on purpose
- [ ] An SBOM of the release jar and of the image, and a provenance attestation that a later job verifies
- [ ] ZAP against an OpenAPI description of the service (API scan), for findings beyond headers
- [ ] A Gradle variant of the service and the same gates, to show what changes when the build tool does
- [ ] Read the SonarQube gate from a server that exists (an environment variable) and fall back to the throw-away one
- [ ] Verify the release jar from a clean checkout: rebuild it and compare the sha256 with `SHA256SUMS`
