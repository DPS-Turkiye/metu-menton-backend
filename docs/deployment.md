# Deployment

## CI

`.github/workflows/ci.yml` runs on every pull request (stacked PRs included) and on pushes to `main`:

1. `./mvnw spotless:check`: formatting
2. `./mvnw checkstyle:check`: lint
3. `./mvnw clean verify`: zero-warning build with Error Prone and NullAway, every test, `ArchitectureTest` and the
   coverage floor

`make ci` runs the same steps locally. Maven runs inside the `eclipse-temurin:25-jdk-jammy` container through
`./mvnw`, as on a developer machine; the Maven repository is cached in `.ci/m2`.

## Releasing

Pushing a tag `v*` runs `.github/workflows/deploy.yml`, which deploys to ODTÜ's temporary AWS server (eu-north-1):

1. `./mvnw -B clean test` (the tagged commit already passed CI's full `verify` on `main`).
2. GitHub OIDC assumes the deploy role named by the repository variable `ODTU_DEPLOY_ROLE_ARN`; no AWS keys are
   stored in GitHub.
3. Over SSM, the document `odtu-menton-deploy` runs `ops/deploy-component.sh backend <tag>` on the instance tagged
   `MentonDeployTarget=odtu`. The server builds the image itself (no registry), keeps the running web, migrates, waits
   for `/api/health` to report the tag and smoke-tests the site. It deploys only commits on `main`.
4. The job waits for the result (up to 75 minutes) and prints the step and smoke-test lines; the full log stays on
   the server.

A manual run (**Actions → Deploy → Run workflow**) on `main` or a `v*` tag does the same; `main` deploys its commit.
While the server's pause file exists (a demo), a run deploys nothing and says `DEPLOY PAUSED`. The server-side
scripts and the IAM policies live in the ODTÜ deploy bundle (`deploy/odtu`). The web deploys the same way on every
push to its `main`.

## In production

- A single EC2 instance runs the container behind CloudFront. The scheduled jobs assume one instance; running more
  would send reminders twice.
- The container's port must accept traffic only from CloudFront: the per-IP limits read `X-Forwarded-For`.
- PostgreSQL with pgvector, Redis (ElastiCache) with a password, S3 and SES in `eu-central-1`.
- Settings come from the server's environment (see [Configuration](configuration.md)). A released image refuses to
  start with a placeholder `SECURITY_PASSWORD`, `DATABASE_PASSWORD` or `REALTIME_TOKEN_SECRET`.
- Flyway migrates the database at start-up and Hibernate validates the entities against it; a migration that does
  not match its entity stops the start (see [Development](development.md#database-migrations)).
- The JVM heap is half the machine's memory (`JAVA_OPTS` in the `Dockerfile`), so a larger instance needs no image
  change.
- `/api/health` is the health check.
