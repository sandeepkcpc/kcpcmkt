# DEV / PROD deployment on a single GCP VM

Two fully isolated Docker Compose stacks, `kcpc-dev` and `kcpc-prod`, running side by side on one
GCP Compute Engine VM. Neither stack shares a database, volume, network, container name, `.env`
file, or Compose project with the other. The already-verified local Mac workflow
(`docker compose up -d --build` from the repo root) is untouched by any of this - see
[`../docker-compose.yml`](../docker-compose.yml).

```text
GCP VM
├── /opt/kcpc-scripts/         deploy/scripts/*.sh - refreshed from git on every deploy, shared
│                               (parameterized by env dir, holds no env-specific values itself)
├── /opt/kcpc-dev/
│   ├── docker-compose.yml     copied once from deploy/dev/docker-compose.yml
│   ├── nginx.conf             copied once from deploy/dev/nginx.conf
│   ├── .env                   real secrets - created once from deploy/dev/.env.example, never committed
│   └── .env.previous          rollback record, written by deploy.sh
└── /opt/kcpc-prod/
    ├── docker-compose.yml     copied once from deploy/prod/docker-compose.yml
    ├── nginx.conf             copied once from deploy/prod/nginx.conf
    ├── .env                   real secrets - created once from deploy/prod/.env.example, never committed
    └── .env.previous          rollback record, written by deploy.sh
```

`docker-compose.yml`/`nginx.conf` are copied to the VM **once**, during manual first-time setup
(see the checklist below) - normal deploys only ever touch the app container's image tag (via
`.env`'s `IMAGE_TAG`), never these files. If you change either file, re-copy it manually and
`docker compose up -d` the affected service(s) yourself; CI does not manage them.

## Spring profile strategy

Three deployment targets exist today: the local Mac Compose stack, GCP DEV, and GCP PROD. All
three run `SPRING_PROFILES_ACTIVE=docker` - **no new `docker-dev`/`prod` Spring profile was
introduced.**

Why: the `docker` profile is already exactly what every one of these three targets needs -
100% environment-variable-driven (`DB_HOST`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`/`COOKIE_SECURE`/
`DRIVE_*`, no hardcoded values) and it never references `db/migration-demo` (only the `dev`
profile, meant for a developer's own laptop, does that - see
[`../src/main/resources/application.yml`](../src/main/resources/application.yml)). The only thing
that actually differs between local/DEV/PROD is the *values* of those environment variables, which
already live entirely in each environment's own `.env` file. Introducing `docker-dev`/`docker-prod`
profiles would mean three copies of identical YAML for zero behavioral difference - the opposite of
"prefer reusable steps, avoid duplicated logic."

`DockerFlywayProfileConfigurationTest` (parses `application.yml` directly, no Spring context
needed) is the regression guard proving `db/migration-demo` can never reach the `docker` profile -
since all three deployment targets share that one profile, this single test covers all three.

## Separate databases

| | DEV | PROD |
|---|---|---|
| DB name | `kcpc_dev` | `kcpc_prod` |
| Volume | `kcpc_dev_pgdata` (Compose-managed) | `kcpc_prod_pgdata` (**external**, see below) |
| Compose project | `kcpc-dev` | `kcpc-prod` |
| Container | `kcpc-dev-postgres` | `kcpc-prod-postgres` |

Never point one stack's `DB_HOST`/`DB_NAME` at the other's container/database - each stack's own
`kcpc-dev-postgres`/`kcpc-prod-postgres` service name only resolves inside that stack's own
Compose-created network, so this is enforced structurally, not just by convention.

## Production DB volume protection

DEV's `kcpc_dev_pgdata` is a normal Compose-managed volume - `docker compose down -v` inside
`/opt/kcpc-dev` deletes it, which is fine for a non-production environment you may want to reset.

PROD's `kcpc_prod_pgdata` is declared `external: true` in
[`prod/docker-compose.yml`](prod/docker-compose.yml). Compose never creates or deletes a volume it
did not create itself - so `docker compose down -v` inside `/opt/kcpc-prod` **cannot** delete it,
and `up` fails loudly if it doesn't already exist rather than silently creating a fresh, empty one
under the same name. Provision it once, out-of-band, before the first PROD deploy:

```bash
docker volume create kcpc_prod_pgdata
```

## Database backup

[`scripts/backup-postgres.sh`](scripts/backup-postgres.sh) runs `pg_dump` inside the Postgres
container, gzips it, uploads to a GCS bucket, then deletes the local copy - the VM is never the
only place a backup lives.

**Recommended schedule**: daily, via cron on the VM (`crontab -e` as the `deploy` user):

```cron
0 3 * * * /opt/kcpc-scripts/backup-postgres.sh /opt/kcpc-prod gs://GCS_BACKUP_BUCKET >> /var/log/kcpc-backup.log 2>&1
```

`GCS_BACKUP_BUCKET` - `REQUIRES USER VALUE`.

**Retention**: set once as a GCS bucket lifecycle rule (not scripted per-backup, so there is one
authoritative rule):

```bash
cat > /tmp/backup-lifecycle.json <<'EOF'
{"rule": [{"action": {"type": "Delete"}, "condition": {"age": 30}}]}
EOF
gsutil lifecycle set /tmp/backup-lifecycle.json gs://GCS_BACKUP_BUCKET
```

**Restore**:

```bash
deploy/scripts/restore-postgres.sh /opt/kcpc-prod gs://GCS_BACKUP_BUCKET/postgres/<file>.sql.gz --force
```

`--force` is mandatory and is the only confirmation this script asks for - it overwrites every row
in the target database. See the script's own header comment for the full rationale.

## Docker image strategy

The existing, already-tested [`../Dockerfile`](../Dockerfile) is reused completely unchanged -
same multi-stage build, same non-root runtime user, same `java -jar` executable WAR. GitHub
Actions builds it once per push and pushes two tags to Artifact Registry:

```text
REGION-docker.pkg.dev/PROJECT_ID/kcpc/kcpc-app:<git-sha>   # immutable, what actually gets deployed
REGION-docker.pkg.dev/PROJECT_ID/kcpc/kcpc-app:dev          # or :prod - floating, manual-recovery convenience only
```

Both `dev/docker-compose.yml` and `prod/docker-compose.yml` reference `${AR_IMAGE}:${IMAGE_TAG}`.
PROD's `IMAGE_TAG` has **no fallback default** (`${IMAGE_TAG:?...}`) - Compose refuses to start if
it isn't explicitly set, so a real PROD deploy can never silently run on a stale/wrong tag.

**Image retention**: old Artifact Registry images are never deleted automatically by any workflow
or script here. If storage cost ever matters, set an Artifact Registry cleanup policy
(`gcloud artifacts repositories set-cleanup-policies`) as a deliberate, separate, out-of-band
action - never as a side effect of a normal deploy.

## Artifact Registry (manual, one-time)

```bash
gcloud artifacts repositories create kcpc \
  --repository-format=docker \
  --location=REGION \
  --description="KCPC application images (dev + prod)"
```

`REGION`, `PROJECT_ID` - `REQUIRES USER VALUE`. One repository, shared by both `:dev`-tagged and
`:prod`-tagged images - they are just different tags of the same `kcpc-app` image name, isolated
by tag rather than by repository (matches the image-naming example given in the task).

## GitHub Actions architecture

```text
.github/workflows/
├── deploy.yml        reusable (workflow_call): test -> build+push -> deploy, used by both callers
├── deploy-dev.yml     push to `dev` branch -> calls deploy.yml with the DEV inputs
└── deploy-prod.yml    push to `main` branch -> calls deploy.yml with the PROD inputs
```

One reusable workflow, two thin callers differing only in `inputs:` (`gh_environment`, `env_dir`,
`image_tag_suffix`) - no duplicated test/build/deploy logic between DEV and PROD.

## GitHub Environments

Two GitHub Environments (Settings -> Environments): **`development`** and **`production`**. Each
carries its own `vars` (non-secret config) and `secrets`, completely isolated from the other -
a workflow run against the `development` Environment physically cannot read `production`'s values,
so there is no way for a DEV deploy to accidentally use a PROD secret.

Put a "Required reviewers" protection rule on **`production`** if you want manual approval before
every PROD deploy - this needs no workflow change; `deploy.yml`'s `build-and-push`/`deploy` jobs
already declare `environment: ${{ inputs.gh_environment }}`, so GitHub pauses those jobs for
approval automatically whenever the target Environment has that rule configured.

## GCP authentication from GitHub (Workload Identity Federation)

No service-account JSON key is ever stored in GitHub. `google-github-actions/auth@v2` exchanges
GitHub's own OIDC token for short-lived GCP credentials via a Workload Identity Pool.

```bash
# One-time, per GCP project - REQUIRES USER VALUE: PROJECT_ID, GITHUB_ORG, GITHUB_REPO
gcloud iam workload-identity-pools create "github-pool" \
  --location="global" --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" --workload-identity-pool="github-pool" \
  --display-name="GitHub OIDC" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='GITHUB_ORG/GITHUB_REPO'" \
  --issuer-uri="https://token.actions.githubusercontent.com"

gcloud iam service-accounts create kcpc-deployer \
  --display-name="KCPC GitHub Actions deployer"

gcloud iam service-accounts add-iam-policy-binding \
  "kcpc-deployer@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/GITHUB_ORG/GITHUB_REPO"
```

Set as `development` and `production` Environment **variables** (not secrets - these identify
*which* WIF provider/SA to use, they are not themselves credentials):

```text
GCP_WORKLOAD_IDENTITY_PROVIDER = projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider
GCP_DEPLOY_SERVICE_ACCOUNT     = kcpc-deployer@PROJECT_ID.iam.gserviceaccount.com
```

### Required IAM roles (least privilege - never Owner/Editor)

Granted to `kcpc-deployer@PROJECT_ID.iam.gserviceaccount.com`:

| Role | Why |
|---|---|
| `roles/artifactregistry.writer` | push images to the `kcpc` repository |
| `roles/iap.tunnelResourceAccessor` | SSH to the VM through IAP without a public IP/open port 22 |
| `roles/compute.osLogin` | log in to the VM as the (non-root) `deploy` OS user |
| `roles/compute.viewer` | resolve the VM's instance metadata for `gcloud compute ssh`/`scp` |

No broader `compute.instanceAdmin`/`Editor`/`Owner` role is needed - the deploy identity never
creates, deletes, or reconfigures the VM itself, only logs in as an unprivileged OS user and runs
scripts that only touch `/opt/kcpc-dev`, `/opt/kcpc-prod`, and `/opt/kcpc-scripts` (owned by that
same `deploy` user - see the manual setup checklist).

## Deployment to the VM

Every deploy touches **only the app container**:

```bash
docker compose pull kcpc-dev-app          # or kcpc-prod-app
docker compose up -d --no-deps kcpc-dev-app
```

`--no-deps` means Postgres is never recreated or restarted for an ordinary release.
[`scripts/deploy.sh`](scripts/deploy.sh) wraps this with: a full `.env` snapshot before touching
anything, waiting on the app container's own Docker healthcheck, and automatically restoring the
previous image + `.env` if the new one never becomes healthy.

If `nginx.conf` changes, redeploy only that service by hand:
`docker compose up -d --no-deps kcpc-dev-nginx` (not part of the automated CI flow - see "runtime
layout" above).

## Environment-variable matrix

| Variable | DEV | PROD |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | `docker` |
| `COOKIE_SECURE` | `false` until DEV has real HTTPS | `true` once PROD has real HTTPS |
| `DB_HOST` | `kcpc-dev-postgres` | `kcpc-prod-postgres` |
| `DB_PORT` | `5432` | `5432` |
| `DB_NAME` | `kcpc_dev` | `kcpc_prod` |
| `DB_USER` | `kcpc_app` | `kcpc_app` |
| `DB_PASSWORD` (`APP_DB_PASSWORD`) | *distinct secret* | *distinct secret* |
| `MIGRATOR_DB_PASSWORD` | *distinct secret* | *distinct secret* |
| `APP_SECURITY_JWT_SECRET` | *distinct secret* | *distinct secret* |
| `AR_IMAGE` | `REGION-docker.pkg.dev/PROJECT_ID/kcpc/kcpc-app` | same (same repo, different tag) |
| `IMAGE_TAG` | git SHA (default `dev` only for manual recovery) | git SHA (**required**, no default) |
| `DRIVE_ENABLED` | `false` until DEV Drive is intentionally configured | `false` until PROD Drive is intentionally configured |
| `DRIVE_SERVICE_ACCOUNT_KEY` | DEV-only service account, if enabled | PROD-only service account, if enabled |
| `DRIVE_SHARED_DRIVE_ID` | *DEV test Shared Drive* | *PROD Shared Drive* |
| `DRIVE_ROOT_FOLDER_ID` | `KCPC DEV CONTENT` folder ID | `KCPC PROD CONTENT` folder ID |
| `DRIVE_IMPERSONATE_USER` | optional | optional |

No actual secret values are reproduced here or anywhere in this repo - see `deploy/dev/.env.example`
and `deploy/prod/.env.example` for the names with placeholder values only.

## Google Drive isolation

DEV and PROD must reference **different** `DRIVE_SHARED_DRIVE_ID`/`DRIVE_ROOT_FOLDER_ID` values -
e.g. two folders in the same Shared Drive (`KCPC DEV CONTENT`, `KCPC PROD CONTENT`) or two separate
Shared Drives entirely. There is nothing in the application itself that prevents DEV from writing
into PROD's folder if given PROD's ID by mistake - `DriveProvisioningService` provisions wherever
`app.drive.root-folder-id` points, unconditionally. The isolation is entirely a matter of never
putting the same folder ID in both `.env` files - `REQUIRES USER VALUE` for both real folder IDs.

## Ports

| | Internal (app) | Host-published |
|---|---|---|
| DEV | 8080 (never published) | 8081 (nginx) |
| PROD | 8080 (never published) | 80 / 443 (nginx) |

Neither app container ever publishes 8080 to the host - only nginx is reachable from outside the
VM, in both stacks. If DNS is available, prefer `dev.example.com` / `app.example.com`
(`REQUIRES USER VALUE`) pointed at the VM instead of exposing DEV's raw `:8081` publicly.

## Nginx

Separate `nginx.conf` per environment (`deploy/dev/nginx.conf`, `deploy/prod/nginx.conf`), both
already carrying: forwarded headers (`X-Real-IP`/`X-Forwarded-For`/`X-Forwarded-Proto`),
`client_max_body_size 10M` (comfortably above the app's own 5MB CSV-import multipart limit), and
no development-only configuration. PROD's carries a commented-out HTTPS `server` block ready to
enable once a certificate exists - see that file's own header comment for the exact command
(`certbot --nginx -d app.example.com`) and the `COOKIE_SECURE=true` flip that must happen alongside
it, never before. Postgres is never exposed through nginx or any published port in either stack.

## Firewall / networking

```text
Public (0.0.0.0/0):  80, 443
```

Do **not** open `5432` (Postgres) or `8080` (app, bypassing nginx) publicly in either stack - both
already only listen on the Docker-internal network by default (`expose`, not `ports`, in both
`docker-compose.yml` files). SSH access to the VM should go through IAP tunneling
(`gcloud compute ssh --tunnel-through-iap`), not a public port-22 firewall rule - see the IAM
section above. If DEV needs to be reachable for QA on `:8081` without going fully public, restrict
that firewall rule's source range to your office/VPN CIDR rather than `0.0.0.0/0`
(`REQUIRES USER VALUE`).

```bash
# REQUIRES USER VALUE: VPC_NETWORK, and the DEV source range if restricting it
gcloud compute firewall-rules create kcpc-allow-http-https \
  --network=VPC_NETWORK --allow=tcp:80,tcp:443 --source-ranges=0.0.0.0/0

gcloud compute firewall-rules create kcpc-allow-iap-ssh \
  --network=VPC_NETWORK --allow=tcp:22 --source-ranges=35.235.240.0/20
```

## Health endpoint

`/actuator/health` (Spring Boot Actuator, added this phase) replaces `/login` as the deployment
healthcheck signal everywhere - the local stack, DEV, and PROD. Only the `health` endpoint is
exposed (`management.endpoints.web.exposure.include: health`) and it only ever reports the
aggregate status (`show-details`/`show-components: never`) - `{"status":"UP"}`, nothing else, so
an unauthenticated healthcheck can never leak datasource/internal details. Postgres connectivity
is folded into that one status automatically via Spring Boot's auto-configured DataSource health
indicator - no custom health-indicator code was written.

`deploy.sh` polls this via the app container's own Docker `HEALTHCHECK` status (`docker inspect`),
not by curling through nginx - deliberately independent of whether nginx itself is mid-restart.

## Rollback

`.env.previous` (a full snapshot, not just the image tag) is written before every deploy attempt.

- **Automatic**: if the new image never becomes healthy, `deploy.sh` restores `.env.previous` and
  redeploys it itself, before the GitHub Actions job even finishes - no separate rollback step
  needed for the common case.
- **Manual**: `deploy/scripts/rollback.sh /opt/kcpc-prod [target-git-sha]` - with no target, rolls
  back to whatever `.env.previous` records; with an explicit SHA, deploys that exact deterministic
  image instead (for rolling back further than one step, or to a known-good tag from Artifact
  Registry history).

Old Artifact Registry images are never deleted automatically (see "Image retention" above), so any
previously-deployed SHA remains pullable indefinitely unless someone deliberately prunes it.

## Production bootstrap safety

A fresh PROD database gets **base Flyway migrations only** - see "Spring profile strategy" above;
this is the same guarantee already audited and tested for the local Docker stack, unchanged. No
demo users, demo grants, test Content IDs, assignments, review cycles, publication events, KPI
history, or test audit logs are ever in any migration under `db/migration` (only `db/migration-demo`
has any of that, and it is structurally unreachable outside the `dev` profile).

## Initial CEO credential - go-live blocker

The migration-seeded CEO account (`ceo@kcpcbandhani.local`, a publicly-documented password) is the
**only** account that will exist on a fresh PROD database, and - as found in the earlier seed-data
audit - there is currently no in-app way to change a password (`User.changePasswordHash()` has zero
callers anywhere in the codebase). This has not changed as part of this deployment work.

**This is a go-live blocker, not something CI automates.** Before real users touch PROD:

1. Log in as the seeded CEO once.
2. Create a second CEO-Business-Role account with a real, private password through the existing
   Create User form.
3. Verify that second account logs in successfully.
4. Deactivate the original seeded account (no self-lockout guard exists on `deactivate`, so verify
   step 3 first).

The bootstrap password is never referenced in any GitHub Actions workflow, script, or log output in
this deployment structure - it is a one-time manual login step, performed by a human, once, outside
of CI.

## Manual GCP/GitHub setup checklist

Everything below is a one-time, out-of-band action - none of it is automated by the workflows/
scripts in this repo, and none of it should be, per the task's own instructions.

1. Create the GCP project (or identify the existing one) - `REQUIRES USER VALUE: PROJECT_ID`.
2. Enable required APIs: `gcloud services enable artifactregistry.googleapis.com compute.googleapis.com iamcredentials.googleapis.com iap.googleapis.com`.
3. Create the Artifact Registry repository (see "Artifact Registry" above).
4. Create/identify the Compute Engine VM - `REQUIRES USER VALUE: VM_NAME, ZONE, VPC_NETWORK`.
   Install Docker Engine + the Compose plugin on it (standard `apt` install of `docker-ce`/
   `docker-compose-plugin` - not scripted here, this is base OS provisioning).
5. On the VM: create an unprivileged `deploy` OS user; `mkdir -p /opt/kcpc-scripts /opt/kcpc-dev /opt/kcpc-prod` owned by that user.
6. Enable OS Login on the VM/project (`gcloud compute instances add-metadata ... --metadata enable-oslogin=TRUE`, or project-wide).
7. `docker volume create kcpc_prod_pgdata` on the VM (see "Production DB volume protection").
8. Copy `deploy/dev/docker-compose.yml` + `deploy/dev/nginx.conf` to `/opt/kcpc-dev/`, and the PROD
   equivalents to `/opt/kcpc-prod/`.
9. `cp deploy/dev/.env.example /opt/kcpc-dev/.env` and `cp deploy/prod/.env.example /opt/kcpc-prod/.env`
   on the VM, then fill in real, distinct values for every `REQUIRES USER VALUE`/`changeme-*` entry.
10. Set up Workload Identity Federation + the `kcpc-deployer` service account + IAM roles (see
    above). `REQUIRES USER VALUE: GITHUB_ORG, GITHUB_REPO`.
11. Create the `development` and `production` GitHub Environments; set their `vars`
    (`GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`, `GCP_PROJECT_ID`,
    `GCP_REGION`, `GCP_VM_ZONE`, `GCP_VM_NAME`, `AR_REPOSITORY=kcpc`) - see "GitHub Environments".
12. Optionally add a "Required reviewers" protection rule on the `production` Environment.
13. Create the GCS backup bucket and set its lifecycle policy (see "Database backup").
    `REQUIRES USER VALUE: GCS_BACKUP_BUCKET`.
14. Add a cron entry on the VM for `backup-postgres.sh` (see "Database backup").
15. Create the firewall rules (see "Firewall / networking").
16. If using domains: point DNS at the VM's external IP, then provision TLS (see "Nginx").
17. Perform the first DEV and PROD deploys, then complete the "Initial CEO credential" steps above
    on PROD before any real user access.
