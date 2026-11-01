# Design notes

Why secure-pipeline is built the way it is. The README says what the pipeline does; this says what was decided, what
else was considered and how it was checked. The decisions are in the order a reader usually asks about them.

## The problem in one paragraph

A security stage in a pipeline is easy to add and easy to get wrong without noticing. A scanner pointed at the wrong
path, a quality gate with no conditions, a tool that exits 0 when it cannot reach its database: all of them show a green
tick. The question this repository asks is not "which scanners are there" but "has each one ever been seen to say no".
So every stage has a twin: the same tool, run on an input that is wrong on purpose, and required to fail for the right
reason.

## Decisions

### Every gate is shown failing

| Stage | Wrong on purpose | What the job requires |
|---|---|---|
| OSV-Scanner | `demo/vulnerable-dependency/vulnerable.pom`, log4j-core 2.14.1 | exit status 1 and `CVE-2021-44228` or its GHSA id in the output |
| Trivy | `alpine:3.12` | exit status 1 and a `Total: N` with N above zero |
| SonarQube | `demo/bad-code`: hard-coded password, MD5, SQL concatenation, unclosed stream, `a == a` | the analysis does not succeed **and** the gate reports ERROR |
| ZAP | `python -m http.server`, which sends no defensive headers | exit status 2 and one of the header rules (10020, 10021, 10038) in the findings |

The check is on the exit status **and** on the content of the output. A status check alone passes when the tool fails
for a wrong reason, such as a network error or a bad flag, and that is precisely the case this is meant to catch.

### The quality gate is code

SonarQube's built-in gate ("Sonar way") judges *new* code only. On a first analysis nothing is new, so there was nothing
for it to fail on, and the bad-code project passed it. The first version of this pipeline was green for code with a
hard-coded password, which is why the "must fail" job exists.

`sonar/quality-gate.json` lists eight conditions (coverage, bugs, vulnerabilities, reviewed security hotspots,
duplication, and the reliability, security and maintainability ratings). `scripts/sonarqube.sh` creates the gate through
the API, creates the project, selects the gate for it and prints every condition with its actual value, so a failing
build says which condition failed and by how much. The numbers are mine; on a real project the team agrees them.

Not used: SonarQube's own "Sonar way", which would also have passed; the SonarCloud service, which needs an account and a
token and cannot be a throw-away.

### SonarQube is a throw-away container

The job starts SonarQube Community from an image pinned by digest, waits for it, sets a generated admin password,
analyses and discards it. That costs a minute or two and keeps the result reproducible. A company normally has a
long-lived server; the GitLab version expects `SONAR_HOST_URL` and `SONAR_TOKEN` for that case and fails loudly if they
are missing.

### Pinned, and checked

- Actions are pinned to a commit SHA, with the tag in a comment. Docker images are pinned to a digest, with what the
  digest was in a comment. Dependabot proposes updates of both, monthly and grouped, so a pin is a reviewed change and
  not a silent one.
- Downloaded tools go through `scripts/fetch-tool.sh`, which refuses a file whose sha256 differs from `tools.lock`.
  The first sums were taken on first use (the download was blocked from the machine where this was written, so CI printed
  the real hashes), which is "trust on first use". To avoid stopping there, `scripts/verify-lock.sh` downloads each
  publisher's own checksum file (`osv-scanner_SHA256SUMS`, `trivy_0.76.1_checksums.txt`) and requires the lock to agree.
- The Dockerfile's base image must carry a digest, and the pipeline fails with the digest the tag points to today if not.

### Failures that explain themselves

Early failures showed only "Process completed with exit code 1", which made them hard to diagnose from outside the
runner, so it became a rule: any step that can fail writes the tail of the tool's output as an
annotation (`scripts/annotate-tail.sh`), and the scripts print `::error` lines. The reason is then on the run page. The
passing runs write notices too (the gate's conditions, the ZAP and Trivy results, the image digests), so the proof that
a gate failed on the wrong input is visible without opening a log.

Details learned on the way, kept because they are the sort of thing that costs an afternoon: GitHub runs `bash -e`, so
a failing command in a pipe can end a step before the next line prints; a variable written to `$GITHUB_ENV` is visible only
from the next step; BuildKit does not leave base images in the local store, so `docker image inspect` needs a `docker pull`
first.

### ZAP: one rule ignored, and why

ZAP's baseline is passive: it reads responses. With the service's headers in place it reports one warning, rule 10049
"Non-Storable Content", because the service sends `Cache-Control: no-store`. That is on purpose, so
`scan/zap-baseline.conf` ignores the rule and says why, in the file and next to the pipeline. Any other warning fails the job:
the default of the baseline is that warnings do not fail the build, which makes the scan decoration.

### A vulnerable pom that is not called pom.xml

Committing a dependency on log4j 2.14.1 as `pom.xml` makes GitHub raise dependency alerts for the repository, which is
noise for a reader and a bad look for a security repository. The file is called `vulnerable.pom` and the scan is told how
to read it (`--lockfile pom.xml:demo/vulnerable-dependency/vulnerable.pom`).

### Releases come from the changelog

`release.yml` runs when `CHANGELOG.md` changes on main, takes the first numbered heading, and stops if that release
exists. Before building it checks that `service/pom.xml` has the same version, then runs `mvn verify`, names the jar after
the version and attaches it with a `SHA256SUMS` file. A change that only edits "Unreleased" releases nothing.

### A GitLab version, honestly

The job description this was written for named GitLab CI. The file is a translation of the GitHub stages, checked against
GitLab's own schema (`check-jsonschema --builtin-schema vendor.gitlab-ci`) on every push, with a deliberately broken file
that the validator must reject. That proves the syntax and the stage structure. It does not prove a runner accepts it,
that the Docker-in-Docker job works or that the images start, and the file says so at the top.

## How it was checked

- Every workflow runs through `actionlint` (with `shellcheck` on the scripts) before it is pushed.
- The service has 22 JUnit tests and a JaCoCo floor of 80 % of lines in the build; it measured 95.8 % in CI.
- The workflow itself is the main check: each "must fail" job is a test of a scanner. One of them, the SonarQube gate,
  was first seen *not* failing on the bad code, which is how the gate-as-code decision came about.
- The Java was built and tested only in GitHub Actions. The machine it was written on cannot reach Maven Central.
- The GitLab file is validated, not run.

What was **not** done: no mutation check of the Java code, no run of the GitLab pipeline, no comparison of the gate
numbers against a real team's rules.

## What would be changed next

These are in [ROADMAP.md](../ROADMAP.md) in the order they would be tackled:

1. Run the GitLab version on GitLab, read the image digests from a registry, and pin them.
2. Scan for secrets in the history (and show a leaked fake key failing), the one common stage that is missing.
3. A hadolint check of the Dockerfile, with a Dockerfile that is wrong on purpose.
4. An SBOM and a provenance attestation of the release jar, verified in a later job.
5. Nuclei or ZAP's API scan against an OpenAPI description, for active findings.

## Questions this design usually draws

**Why a quality gate in JSON and not set up by hand in the SonarQube UI?** A gate set up by hand cannot be reviewed, is
lost with the server, and was not there on the first run. As code it is a diff in a pull request.

**Why does the pipeline fail the build on any ZAP warning?** The baseline's default exit code is 0 for warnings. A
scan that only warns is a report nobody opens. One rule is ignored on purpose, in a file, with its reason.

**What happens when a new vulnerability is published for something you already use?** The OSV job fails on the next
push, and Dependabot proposes the bump. The first is a build break nobody asked for, which is how it should feel. A
scheduled run of the pipeline would catch it without a push; that is on the [roadmap](../ROADMAP.md).

**How do you know a scanner is not just broken?** It is shown failing on an input that is wrong on purpose, every run,
with the reason checked in its output.

**Why pin by digest if Dependabot moves the pins anyway?** Because the move is a reviewed commit with a diff, and the
build in between is reproducible. A mutable tag can change what runs without anything in the repository changing.

**Would this work with GitLab and a company SonarQube?** The structure carries over, and the GitLab file is the starting
point, but it has not been run and the company's gate, secrets and runners are not known here.

## Three-minute demo

```sh
git clone https://github.com/ErenOzgu/secure-pipeline && cd secure-pipeline
cat .github/workflows/pipeline.yml                     # six jobs, one per question
cat sonar/quality-gate.json                            # the gate, as code
cat tools.lock && scripts/verify-lock.sh               # what the pipeline trusts, checked against the publishers
```

Then open the latest run of the *pipeline* workflow on GitHub: the annotations list the gate of the service (all OK)
next to the gate of the bad code (reliability E, security D, three bugs, two vulnerabilities), and the Trivy and OSV
results for the inputs that must fail.
