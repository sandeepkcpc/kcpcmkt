# Cache-Busting — Deployment Safety & Change Audit

**Audit date:** 2026-09-04
**Branch:** `dev` @ `e73d004` + 43 uncommitted files
**Scope:** independent verification of the cache-busting change against the actual working tree and deployment configuration
**Method:** static inspection of the working tree, `git diff` against HEAD, WAR inspection, a live Docker + Nginx stack, and two full 613-test regression runs (modified tree vs. pristine HEAD) from a freshly reset database

> No code was modified while producing this report.

---

## Executive summary

| Question | Answer |
|---|---|
| Works after production deployment | **YES** — verified end-to-end on a live Docker + Nginx stack |
| Modifies production business data | **NO** — zero DB changes of any kind |
| Makes production slower | **NO** — one cached MD5 per file per JVM lifetime |
| Requires Nginx change | **NO** — `nginx.conf` untouched and byte-identical |
| Requires Docker/GCP change | **NO** |
| Users must clear browser cache | **NO** — that is the entire point of the change |
| New test failures | **ZERO** (613 tests, 605 passed; every failure reproduces on pristine HEAD) |

**Verdict: APPROVED WITH CONDITIONS.** The change is production-safe as built. Two conditions are documented in §15 — neither blocks deployment, and neither is a defect in the deployed configuration. One is a **latent** bug that activates only if the app is ever moved off the root context path; the other is a redundancy worth tidying.

---

## 1. Root cause

### What caused it

Two independently reasonable decisions that combine into a bug.

**(a) Nginx makes static assets cacheable for 30 days.** `nginx.conf:44-48` (identical in `deploy/dev/nginx.conf` and `deploy/prod/nginx.conf:56-60`):

```nginx
location ~* \.(?:css|js|mjs|png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|eot|map)$ {
    proxy_pass http://kcpc-app:8080;
    proxy_hide_header Pragma;
    expires 30d;
}
```

`expires 30d` **replaces** the upstream `Cache-Control`. The application itself sends `no-cache, no-store, max-age=0, must-revalidate` on every response (Spring Security's default `CacheControlHeadersWriter`), so before this location block existed nothing was cacheable at all. The block was a deliberate, correct performance fix.

**(b) Every asset URL was a fixed string.** Every JSP hardcoded the path:

```jsp
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
<script src="${pageContext.request.contextPath}/js/my-work-dashboard.js" defer></script>
```

There was **no versioning of any kind** anywhere in the application — no query parameter, no hash, no build stamp.

### Why users kept getting old CSS

`Cache-Control: max-age=2592000` is an instruction to the browser: *do not ask the server about this URL again for 30 days.* The browser obeys. Because `/css/app.css` was byte-identical as a URL before and after a deployment, the browser had no way to learn that the bytes behind it had changed. It never sent a request, so the server never got a chance to serve the new file.

### Why clearing the cache "fixed" it

Clearing the cache deletes the stored entry for `/css/app.css`. The next page load has no cached copy, so the browser is forced to make a real request, and gets the new file. This treats the symptom — the URL is still unversioned, so the same staleness begins accumulating again immediately.

### Old asset URLs

```
/css/app.css
/js/my-work-dashboard.js
/images/kcpc-logo.png
/images/favicon.ico
```

---

## 2. Exact solution implemented

Verified from the working tree, not from any prior report.

### What `VersionResourceResolver` does

`VersionResourceResolver` is a Spring MVC resource resolver that sits in the static-resource handler chain. It performs a **bidirectional URL mapping**:

- **Outbound (page render):** given the logical path `/css/app.css`, it returns the public path `/css/app-<md5>.css`.
- **Inbound (asset request):** given a request for `/css/app-<md5>.css`, it strips the hash segment, resolves the real file `/css/app.css`, and verifies the hash matches the file's actual content before serving.

There is no rewriting on disk and no build step. Both directions are computed at runtime from the file's bytes.

### How the hash is generated

`ContentVersionStrategy` reads the resource's bytes from the classpath and computes an **MD5 digest**, rendered as 32 lowercase hex characters and inserted before the file extension. It is a pure function of file content — the same bytes always yield the same hash, on any machine, in any build.

### Old URL vs. new URL

| | URL |
|---|---|
| Before | `/css/app.css` |
| After | `/css/app-e7f161f7397461886e56cba591d4e09b.css` |

### What happens when CSS changes

The bytes change → the MD5 changes → the rendered `<link href>` changes. The browser has never seen that URL, so its 30-day cache entry for the *old* URL is irrelevant — a URL it has no entry for is a guaranteed cache miss, and it fetches. **Observed:** `app-e7f161f7…css` → `app-007691d7…css` after a one-line CSS edit and redeploy.

### What happens when CSS does not change

The hash is identical, the URL is identical, the browser serves it from cache with no network request at all. **Observed:** in the same redeploy where the CSS hash changed, `login-password-toggle-7aa9e59c2edfdbde2b2e32de68740fe1.js` kept its URL and was not re-downloaded.

This per-file granularity is precisely why a content hash was chosen over `?v=<app-version>`: a global version stamp invalidates *every* asset on *every* deploy, forcing users to re-download ~500 KB of unchanged JavaScript for a one-line CSS fix.

### Why long caching can safely stay on

Because the URL is now a function of the content, a stale URL cannot exist. `max-age=2592000` on `/css/app-<hash>.css` is safe indefinitely: if that hash is in the HTML, those exact bytes are what the page wants.

The other half is that **HTML is never cached**. Spring Security sends `no-cache, no-store, must-revalidate` on page responses, and Nginx's `expires 30d` does not apply to them (the location regex matches only file extensions; `/login` and `/app/pipeline` have none and fall through to `location /`). So the browser always re-fetches the HTML, always sees current hashes, and only then decides what to fetch. **Verified:**

```
GET /login                        → Cache-Control: no-cache, no-store, max-age=0, must-revalidate
GET /css/app-<hash>.css           → Cache-Control: max-age=2592000
                                    ETag: W/"007691d74dbae81702852bd252234d5a"
```

---

## 3. Complete file change inventory

**43 files changed, 252 insertions(+), 147 deletions(-).**

### A. Cache-busting changes — production code and configuration (2 files)

| File | Change | Why | Production impact |
|---|---|---|---|
| `src/main/resources/application.yml` | Added `spring.web.resources.chain.strategy.content` (paths `/css/**,/js/**,/images/**`), `chain.cache` (profile-split), `resources.cache.cachecontrol` (30d, public), `server.servlet.session.tracking-modes: cookie`, and a `docker`-profile document | Enables the resolver, tunes its cache per environment, makes the app self-sufficient on cache headers, and prevents `;jsessionid=` in asset URLs | Config only. No DB, no schema, no business logic |
| `src/main/java/com/kcpc/mkt/web/mvc/WebMvcConfig.java` | Added `ResourceUrlEncodingFilter` bean; added `ServletContextInitializer` for cookie-only session tracking | The filter is what makes `<c:url>` emit hashed paths — Boot 3.3 does not auto-register it | Two beans. Existing interceptor registrations untouched |

### B. Cache-busting changes — view layer (38 files)

All 38 are the **same mechanical substitution**, applied 142 times:

```diff
- href="${pageContext.request.contextPath}/css/app.css"
+ href="<c:url value='/css/app.css'/>"
```

Converted references by type: **37 CSS**, **66 JS**, **39 images**.

Largest: `deliverable-detail.jsp` (15), `reviews.jsp` (11), `idea-detail.jsp` (7), `reports-kpi-console.jsp` (6), `publish-task-detail.jsp` (5), `my-work.jsp` (5). The remaining 32 files are 1–4 lines each.

No JSP had any markup, logic, conditional, or text changed — only the value of `href`/`src` attributes.

### C. Changes made by the other Claude Code session

A second Claude Code session (`be7b7fb4`) was active in this working tree and made **4 edits**, all complementary to this change and all verified correct:

| File | Change | Assessment |
|---|---|---|
| `fragments/nav.jsp` | Converted 3 `${ctx}/…` refs (logo + 2 header scripts) to `<c:url>` | **Necessary.** These used a `${ctx}` alias, so the main conversion pass missed them. Without this, `header-user-menu.js` and `header-notifications.js` — loaded on *every* page — would have stayed unversioned |
| `WebMvcConfig.java` | Added `sessionTrackingModeInitializer()` bean | **Redundant but defensible** — see §5 |
| `BrandLogoTest.java` | Literal asset assertions → `containsPattern(...(-[0-9a-f]{32})?...)` | **Correct.** Version-tolerant, so it survives future hash changes |
| `LoginPasswordVisibilityToggleTest.java` | Same pattern | **Correct** |
| `PipelineAjaxPartialTest.java` | `"pipeline-dashboard.js"` → `"pipeline-dashboard"` | **Correct.** Note the `doesNotContain` assertion becomes *stricter*, not weaker |

### D. Test-only changes

The 3 test files above. These adapt assertions to the new URL shape. **No test was weakened to hide a failure**, and no test was disabled or deleted.

### E. Explicitly NOT changed (verified via `git status`)

`nginx.conf` · `deploy/dev/nginx.conf` · `deploy/prod/nginx.conf` · `Dockerfile` · `docker-compose.yml` · `deploy/*/docker-compose.yml` · `deploy/scripts/*.sh` · `pom.xml` · **all Flyway migrations** · `src/main/resources/static/css/app.css` · all JS/image/icon assets · `SecurityConfig.java` · every controller, service, entity, and repository.

---

## 4. Exact code-level changes

### 4.1 `application.yml` — resource chain

```yaml
spring:
  web:
    resources:
      chain:
        strategy:
          content:
            enabled: true
            paths: /css/**,/js/**,/images/**
        cache: false          # overridden to true under the docker profile
      cache:
        cachecontrol:
          max-age: 30d
          cache-public: true
```

- **Old behavior:** no resource chain; `/css/app.css` served directly with no `Cache-Control` from the app.
- **New behavior:** requests to `/css/**`, `/js/**`, `/images/**` resolve through the version resolver; responses carry `Cache-Control: max-age=2592000, public`.
- **Runtime impact:** one MD5 per file, memoized. See §8.
- **Database impact:** none.
- **Auth/session impact:** none. Spring Security's rules use wildcards (`/css/**`, `/js/**`, `/images/**`, `SecurityConfig.java:114`), which match hashed filenames identically.

The `cache` split is deliberate: `false` by default so `mvn spring-boot:run` still picks up live CSS edits on a plain refresh (the `spring-boot-maven-plugin` `addResources` behavior the team relies on), `true` under `docker` where files cannot change while the container runs.

```yaml
---
spring:
  config:
    activate:
      on-profile: docker
  web:
    resources:
      chain:
        cache: true
```

`docker` is the profile used by local compose, `deploy/dev`, and `deploy/prod` alike (`SPRING_PROFILES_ACTIVE: docker` in all three), so **every containerised environment gets the cached path**.

### 4.2 `WebMvcConfig.java` — `ResourceUrlEncodingFilter`

```java
@Bean
public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
    return new ResourceUrlEncodingFilter();
}
```

JSTL's `<c:url>` routes its value through `HttpServletResponse.encodeURL()`. This filter wraps the response so that call consults Spring's `ResourceUrlProvider` and substitutes the hashed path.

**This bean is load-bearing.** Verified against `spring-boot-autoconfigure-3.3.5.jar`: `WebMvcAutoConfiguration` registers only the resource-*handler* half of the chain. There is no `ResourceUrlEncodingFilter` bean anywhere in the autoconfiguration. Without this bean, every `<c:url>` silently emits the plain path and the entire change becomes a no-op — failing open, not closed.

**Failure mode is safe:** a path the resolver does not recognise (e.g. `/icons/**`, outside the configured `paths`) is passed through unchanged rather than erroring.

### 4.3 View layer

142 attribute-value substitutions across 38 files. Runtime cost per rendered page is a handful of `ResourceUrlProvider` lookups (typically 5–9: one CSS, one favicon, one logo, 2–6 scripts), each a `ConcurrentHashMap` hit under the `docker` profile.

---

## 5. Session tracking change

### Why it was added

During live verification the very first render produced:

```html
<link rel="stylesheet" href="/css/app-e7f161f7397461886e56cba591d4e09b.css;jsessionid=6DE425930B274168680C7F6E52FC9657">
<script src="/js/login-password-toggle-7aa9e59c2edfdbde2b2e32de68740fe1.js;jsessionid=6DE425930B274168680C7F6E52FC9657"></script>
```

### The mechanism

1. JSP pages create an `HttpSession` by default (no `<%@ page session="false" %>` in this codebase), so a `JSESSIONID` exists.
2. Tomcat's default session tracking allows **both** `COOKIE` and `URL`.
3. On a brand-new session's *first* response the browser has not yet returned the cookie, so `response.encodeURL()` appends `;jsessionid=…` as the URL-rewrite fallback.
4. `<c:url>` calls `encodeURL()` — so this now applied to all ~142 asset links.

### Why this mattered more than cosmetics

Left unfixed, this would have been **worse than the original bug**. Every asset URL would be unique per session, meaning:

- The browser cache key differs for every visitor and every new session → the cache hit rate collapses toward zero.
- Session identifiers leak into page source, browser history, and any intermediary logs.

The codebase already knew about this hazard. `fragments/nav.jsp:160-167` carries a comment explaining that `<c:url>` was *deliberately avoided* for notification links for exactly this reason, citing `MyPerformanceViewLinkTest`.

### Did the app already intend cookie-only sessions?

**Yes, unambiguously.** `SecurityConfig` disables session management entirely (`.sessionManagement(AbstractHttpConfigurer::disable)`), uses `RequestAttributeSecurityContextRepository`, and installs `NullRequestCache` specifically to avoid creating sessions. Authentication is the `KCPC_AT` JWT cookie on every request. URL-based session tracking was never used by anything.

### Effect on login / auth / existing users

- **Login:** none. Authentication is `POST /login` → JWT cookie. Untouched.
- **JWT:** none. `JwtAuthenticationFilter` reads the `KCPC_AT` cookie and never consults `HttpSession`.
- **Existing users:** none. No user needs to re-authenticate; the `JSESSIONID` was never load-bearing.
- **Verified:** `MyPerformanceViewLinkTest` (the test the `nav.jsp` comment cites) passes 4/4, and the full 613-test suite shows no auth or session regression.

### The redundancy

Cookie-only tracking is currently enforced **twice**:

1. `application.yml` → `server.servlet.session.tracking-modes: cookie`
2. `WebMvcConfig.sessionTrackingModeInitializer()` → `servletContext.setSessionTrackingModes(Set.of(COOKIE))`

Both set the same single value, so there is **no conflict** — whichever applies last sets `COOKIE`. This is verified working: the container starts healthy and no `;jsessionid=` appears in any rendered page.

There is a real argument for keeping both: `server.*` properties configure the *embedded* servlet container. If this WAR were ever deployed into an external Tomcat, the property would be ignored while the `ServletContextInitializer` would still apply. Given `packaging=war`, that is not far-fetched.

**Production risk: LOW.** Recommend consolidating to one mechanism for clarity, but this is a tidiness matter, not a deployment blocker.

---

## 6. Database safety

Inspected the final working tree for anything DB-related.

| Check | Result |
|---|---|
| Flyway migrations added | **None** — `git diff --name-only HEAD -- '*.sql'` is empty |
| Existing migrations modified | **None** |
| Schema changes | **None** |
| Data created / updated / deleted | **None** |
| Startup DB writes added | **None** |
| Notification / user / content-plan data touched | **None** |
| DB queries added to the request path | **None** — resource resolution reads the classpath, never the database |
| `src/main/resources/db/**` changed | **No** |

The entire change set is: 1 YAML file, 1 Java config class, 38 JSPs, 3 test files. Not one line touches a repository, entity, service, or migration.

### Verdict

> ## Production data impact: **NONE**

Nothing in this change can read, write, or migrate production business data. The deployment is a code-only rollout.

> **Separate note, not caused by this change:** the local `kcpc-app` container is currently in a restart loop from a pre-existing Flyway checksum mismatch (migrations V1 and V7 were edited after being applied to that local volume). This is a **local development** condition only. It is unrelated to cache-busting, but it will block *any* deployment to that particular container until repaired, and it is worth confirming the same drift does not exist on the dev/prod VMs before deploying. See §13.

---

## 7. Nginx / server safety

### Current caching behavior — unchanged

`nginx.conf`, `deploy/dev/nginx.conf`, and `deploy/prod/nginx.conf` are all **byte-identical to HEAD** (`git status` reports them unmodified).

### Does `expires 30d` still apply to hashed URLs?

**Yes — and this is essential.** The location regex matches on file extension:

```nginx
location ~* \.(?:css|js|mjs|png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|eot|map)$
```

`/css/app-e7f161f7397461886e56cba591d4e09b.css` still ends in `.css`, so it matches, gets `expires 30d`, and gets gzipped by the `gzip_types` list exactly as before. **Verified through the live Nginx container:**

```
GET /css/app-007691d74dbae81702852bd252234d5a.css
  → HTTP/1.1 200
    Cache-Control: max-age=2592000
    ETag: W/"007691d74dbae81702852bd252234d5a"
    Vary: Accept-Encoding
```

The hashed URL and the Nginx rule are complementary: Nginx supplies the long cache lifetime, the hash supplies the invalidation. Neither works well without the other.

| Component | Change required |
|---|---|
| Nginx configuration | **No** |
| Docker configuration (`Dockerfile`, `docker-compose.yml`) | **No** |
| GCP configuration | **No** |
| Production environment variables | **No** |
| `deploy/scripts/*.sh` | **No** |

### Verdict

> ## Production server configuration change required: **NO**

---

## 8. Performance / server speed

### Will this make production slower?

**No.** The added work is one MD5 digest per static file, once per JVM lifetime.

### Is the hash computed per request?

**No — under the `docker` profile it is cached.** `spring.web.resources.chain.cache: true` inserts a `CachingResourceResolver` ahead of the version resolver, memoizing both resolved paths and computed hashes in a concurrent map. Since `SPRING_PROFILES_ACTIVE: docker` is set in the local compose file, `deploy/dev/docker-compose.yml:46`, and `deploy/prod/docker-compose.yml:43`, **every deployed environment gets the cached path.**

Under the default profile (local `mvn spring-boot:run` only) caching is off by design, so a developer's CSS edit is picked up on a plain refresh.

### Cost analysis

| Dimension | Impact |
|---|---|
| **CPU — first request per file** | One MD5 over the file. `app.css` is 204 KB → sub-millisecond. 40 static files total |
| **CPU — subsequent requests** | Zero. Map lookup |
| **CPU — page render** | 5–9 `ResourceUrlProvider` lookups per page, each a `ConcurrentHashMap` get. Immeasurable against JSP rendering and JPA |
| **RAM** | ~40 cache entries (path string + hash + `Resource` handle). Kilobytes |
| **Request processing** | One extra resolver in the static chain. Static assets are already the cheapest requests the app serves — and now they are served *less often*, because caching finally works |
| **JSP rendering** | `<c:url>` replaces raw EL concatenation. Adds a map lookup per asset link |
| **Static asset serving** | Net **improvement**. Before, a returning user re-requested assets whenever their cache expired against an unversioned URL; now unchanged files are never re-requested |
| **Nginx** | No change. Same gzip, same `expires` |
| **Docker** | No change. Same image layers, same base, same entrypoint. WAR is 90 MB before and after |
| **Startup time** | No measurable change. Resolution is lazy — nothing is hashed at boot |
| **Database** | Zero additional queries |

### Before vs. after

| | Before | After |
|---|---|---|
| URL | `/css/app.css` | `/css/app-<md5>.css` |
| Server work per request | serve file | map lookup + serve file |
| Server work per file, ever | — | one MD5 |
| Requests from a returning user | re-fetch on cache expiry | **zero** while content is unchanged |

### Verdict

**Production will not be slower. Net effect is a small improvement in real-world traffic**, because correct long-lived caching finally becomes safe, eliminating repeat asset transfers.

---

## 9. Deployment safety

### Source → Maven/WAR → Docker → Container → Nginx → Browser

**1. Source → WAR.** Verified by rebuilding `target/kcpc-mkt-mvp.war` (90 MB) from the current tree:

```
WEB-INF/classes/static/css/app.css                204353 bytes
WEB-INF/classes/static/js/reviews-workspace.js     58344 bytes
WEB-INF/classes/static/images/kcpc-logo.png       265653 bytes
```

> **Important and easy to misread:** assets are packaged **unversioned**. A search for `app-[0-9a-f]{32}.css` inside the WAR returns **0 matches**. The hash is a *runtime URL mapping*, not a build artifact. There is no build step to add, and nothing about `mvn package` changes.

JSPs are packaged with the new markup — confirmed inside the WAR:

```jsp
<link rel="stylesheet" href="<c:url value='/css/app.css'/>">
```

**2. WAR → Docker image.** `Dockerfile` unchanged. Multi-stage build runs `mvn clean package -DskipTests` and copies the WAR to `/app/app.war`. Since the assets are ordinary classpath files, they ship exactly as before.

**3. Image → container.** `java -jar /app/app.war` with `SPRING_PROFILES_ACTIVE=docker`. The resource chain activates with caching on. **Verified:** container reached `healthy` and served hashed URLs.

**4. Container → Nginx.** Hashed filenames still match the static-extension regex, so they are proxied, gzipped, and given `expires 30d` exactly as before. **Verified** against the live stack.

**5. Nginx → browser.** **Verified** on the running stack:

```html
<link rel="stylesheet" href="/css/app-e7f161f7397461886e56cba591d4e09b.css">
<link rel="icon" href="/images/favicon-d4e6872d414a25432d563f4c7a1026b3.ico">
<script src="/js/login-password-toggle-7aa9e59c2edfdbde2b2e32de68740fe1.js" defer></script>
```

**6. Old browser cache cannot block the new CSS.** Two independent guarantees:

- The HTML is `no-cache, no-store` (Spring Security), and Nginx's `expires` rule does not match extensionless page URLs. The browser *always* re-fetches the HTML.
- The freshly fetched HTML names a URL the browser has never requested. A cache lookup for an absent key is a guaranteed miss.

**Empirically demonstrated** with a cache simulation that was never cleared across a redeploy:

| Visit | Referenced URL | Outcome |
|---|---|---|
| 1 | `app-007691d7…css` | MISS → fetched |
| 2 | `app-007691d7…css` | **HIT** → no request (caching still works) |
| — | *CSS changed, image rebuilt, container redeployed* | |
| 3 | `app-e7f161f7…css` | **MISS → fetched new file, cache never cleared** |
| 4 | `app-e7f161f7…css` | **HIT** → no request |

### Do deployment commands change?

**No.** `deploy/scripts/deploy.sh <env-dir> <image-tag>` is unchanged, as is its healthcheck-and-auto-rollback behavior.

---

## 10. Asset coverage

| Asset type | Location | Versioned | Notes |
|---|---|---|---|
| CSS | `static/css/app.css` | **Yes** | 37 references |
| JavaScript | `static/js/*.js` (37 files) | **Yes** | 66 references |
| PNG | `static/images/kcpc-logo.png` | **Yes** | |
| Favicon | `static/images/favicon.ico` | **Yes** | 37 references |
| SVG icons | `static/icons/platforms/*.svg` (7) | **No** | See below |
| `static/favicon.ico` (root) | | **No** | Not referenced by any JSP |
| Fonts | — | n/a | None in this application |
| Webjars / Swagger UI | | **No** | Not referenced from any JSP; Swagger serves its own assets |

### Remaining unversioned references — 3, all intentional

```
fragments/scope-target-icon.jspf:16      ${pageContext.request.contextPath}/icons/platforms/${cdTargetIconFile}
fragments/pipeline-platform-chip.jspf:28 ${pageContext.request.contextPath}/icons/platforms/${platformIconFile}
fragments/platform-icon-src.jspf:16      ${pageContext.request.contextPath}/icons/platforms/${platformIconFile}
```

**Why `/icons/**` was deliberately excluded:** three JavaScript files build the same icon paths at runtime —

```js
// idea-detail.js:285, reviews-workspace.js:494
return contextPath() + '/icons/platforms/' + (OUTPUT_PLATFORM_ICON_FILES[platformName] || 'generic.svg');
```

Versioning only the JSP-rendered half would leave the JS-built half unversioned, causing the *same* icon to be fetched and cached under two different URLs. Consistent non-versioning is the correct choice. These are 7 static brand SVGs (Instagram, Facebook, YouTube, Threads, Moj, TikTok, generic) that do not change between releases; they remain covered by Nginx's `expires 30d`, i.e. exactly their pre-existing behavior.

**No regression:** these paths behave precisely as they did before this change.

---

## 11. Tests and verification

### Live Docker + Nginx verification

| Check | Result |
|---|---|
| Build (`mvn clean package`) | PASS |
| Docker image builds and container reaches `healthy` | PASS |
| Rendered URL contains version | PASS — `/css/app-e7f161f7…css` |
| Change CSS → rebuild → hash changes | PASS — `e7f161f7…` → `007691d7…` |
| Unchanged JS keeps its hash | PASS — re-cached, not re-fetched |
| Browser fetches new CSS with cache never cleared | PASS |
| Caching still enabled (not disabled) | PASS — repeat visit is a cache hit |
| Asset headers | PASS — `max-age=2592000`, `ETag: W/"<hash>"` |
| HTML stays uncached | PASS — `no-cache, no-store` |
| Revalidation | PASS — `If-Modified-Since` → `304` |
| No `;jsessionid=` in any URL | PASS (after the §5 fix) |

### Full regression suite — from a freshly reset database, both trees

| | With cache-busting | Pristine HEAD (zero changes) |
|---|---|---|
| Test classes | 110 | 110 |
| **Total tests** | **613** | 613 |
| **Passed** | **605** | 604 |
| **Failures** | **8** | 9 |
| **Errors** | **0** | **0** |
| Skipped | 0 | 0 |

Both runs used the project's supported reset (drop + recreate `kcpc_test`, per `README.md:27`), and the working tree was confirmed unmodified between the verification run and this audit (last source edit 10:15; suite ran 12:46–12:51).

### Failure classification

| Class | Modified tree | Pristine HEAD | Classification |
|---|---|---|---|
| `AssignmentManagementQueueTest` | 2F | 2F | **Pre-existing** |
| `EditTaskDetailTest` | 1F | 1F | **Pre-existing** |
| `IdeaCreateModalTest` | 2F | 2F | **Pre-existing** |
| `ShootTaskDetailTest` | 1F | 1F | **Pre-existing** |
| `SubmitIdeaFormTest` | 2F | 2F | **Pre-existing** |
| `ContentDetailPeopleMarksTest` | pass | 1F | **Flaky** — failed only on pristine |

> ### New failures caused by cache-busting: **ZERO**

Identical classes, identical counts. Pristine HEAD failed one *more* test than the modified tree.

### Environmental failures (resolved, not worked around)

Before the database reset, `PlanningSingleFormTest` **hung for 55 minutes at 2.6 GB RSS**. Thread dump showed it CPU-bound inside AssertJ:

```
java.lang.String.substring
org.assertj.core.internal.Strings.removeUpTo
org.assertj.core.internal.Strings.assertContainsSubsequence
PlanningSingleFormTest.java:69
```

Cause: `kcpc_test` had accumulated **9,887 ideas / 8,770 content_plans / 154,956 notifications** across many runs, producing an enormous idea-detail response body that made `containsSubsequence` quadratic. After the reset it **passes in 12.3 s**. `KpiOverviewUpcomingChannelPlanTest`, which had been failing intermittently, also passes.

**No test and no application code was modified to achieve this.**

### Coverage gap to be aware of

`src/test/js/*.test.js` exists, but there is **no `package.json` and no frontend build plugin in `pom.xml`** — these tests are not executed by `mvn test`. Client-side JavaScript behavior therefore has **no automated coverage in CI**, which is directly relevant to the §12 finding below.

---

## 12. Production risk assessment

| Area | Risk | Reason | Verdict |
|---|---|---|---|
| **Database** | **LOW** | Zero migrations, zero schema changes, zero data writes, zero added queries. Verified by diff | Safe |
| **Authentication** | **LOW** | JWT cookie flow untouched. `SecurityConfig` unchanged. Wildcard matchers still match hashed paths. `MyPerformanceViewLinkTest` 4/4 | Safe |
| **Session** | **LOW** | Restricts tracking to cookie-only, which the app already relied on exclusively. Removes an active `;jsessionid=` leak | Safe — an improvement |
| **Nginx** | **LOW** | Byte-identical. Hashed names still match the extension regex — verified live | Safe |
| **Docker** | **LOW** | `Dockerfile` and all compose files unchanged. Same image size and entrypoint | Safe |
| **GCP** | **LOW** | No infrastructure, env-var, or deploy-script change | Safe |
| **Performance** | **LOW** | One cached MD5 per file per JVM. Net improvement from working caches | Safe |
| **Browser caching** | **LOW** | Core objective; verified end-to-end including the never-cleared-cache case | Safe — resolves the reported defect |
| **CSS/JS loading** | **LOW** | All 142 references verified rendering with hashes on a live stack | Safe |
| **Existing UI** | **LOW** | Only `href`/`src` attribute values changed. No markup, layout, or CSS edits | Safe |
| **Business workflow** | **LOW** | No controller, service, entity, repository, or migration touched. 605/613 passing with zero new failures | Safe |
| **JS context-path helpers** | **MEDIUM (latent)** | See below | **Condition — no impact at root context** |

### The one real finding: filename-coupled JavaScript

Four JS files derive the application context path by locating their **own** `<script>` tag by filename:

```js
// reviews-workspace.js:226-232 (same pattern in idea-detail.js:270,
// admin-edit-user-modal.js:39, reports-kpi-ownership-drilldown.js:14)
var script = document.querySelector('script[src*="reviews-workspace.js"]');
if (!script) { return ''; }
var src = script.getAttribute('src');
var idx = src.indexOf('/js/reviews-workspace.js');
return idx > 0 ? src.slice(0, idx) : '';
```

Cache-busting renames the served file to `reviews-workspace-<hash>.js`. The literal substring `"reviews-workspace.js"` no longer occurs, so `querySelector` returns `null` and the function returns `''`.

**Is this a bug in production today? No.** The application runs at the **root context path** — `server.servlet.context-path` is set nowhere in `application.yml`, any compose file, the `Dockerfile`, or any deploy script, and Nginx proxies with `proxy_pass http://kcpc-app:8080;` at `location /`, preserving the URI. At root context the two paths are equivalent:

| | Old behavior | New behavior |
|---|---|---|
| `src` attribute | `/js/reviews-workspace.js` | `/js/reviews-workspace-<hash>.js` |
| `querySelector` | matches | **null** |
| `indexOf` | `0` | — |
| `idx > 0` | false | — |
| **Return value** | **`''`** | **`''`** |

Both return the empty string. **Behavior is identical**, and the `if (!script)` guard means no JavaScript error is thrown.

**When would it break?** Only if the app is ever deployed under a non-root context path (e.g. `/kcpc`). Then the old code returned `"/kcpc"` while the new code returns `""`, breaking AJAX endpoints and platform-icon `src`s in those four modules.

**Why the test suite did not catch it:** these are client-side behaviors, and `src/test/js/*.test.js` is not wired into the Maven build.

**Recommended follow-up (not required for this deployment):** make the lookup hash-tolerant, e.g.

```js
var script = document.querySelector('script[src*="reviews-workspace"]');
var src = script.getAttribute('src');
var idx = src.lastIndexOf('/js/');
return idx > 0 ? src.slice(0, idx) : '';
```

`publication-scope.js` is **not** affected — it reads `data-context-path` from the DOM (`publication-scope.js:214`), which is unrelated to script filenames.

---

## 13. Pre-production deployment checklist

**Build**
- [ ] `git status` shows exactly the 43 expected files, nothing extra
- [ ] `mvn clean package` succeeds
- [ ] `mvn test` — expect **613 tests, 605 passed, 8 failures, 0 errors**, all pre-existing per §11. Reset `kcpc_test` first, or `PlanningSingleFormTest` may hang
- [ ] Confirm no `.sql` file appears in the diff

**WAR verification**
- [ ] `unzip -l target/kcpc-mkt-mvp.war | grep static/css` → `app.css` present, **unversioned** (expected)
- [ ] `unzip -p target/kcpc-mkt-mvp.war WEB-INF/views/login.jsp | grep app.css` → shows `<c:url value='/css/app.css'/>`

**Docker image**
- [ ] Image builds; tag with the git SHA per existing convention
- [ ] Confirm `SPRING_PROFILES_ACTIVE=docker` — **required** for the resolver cache
- [ ] Push to Artifact Registry

**Pre-deploy environment check**
- [ ] Confirm the target VM's database has **no Flyway checksum drift** (the local container currently has this; see §6). `deploy.sh` will auto-roll-back on an unhealthy container, but confirming first avoids a wasted cycle

**Deploy**
- [ ] `deploy/scripts/deploy.sh /opt/kcpc-dev <git-sha>` — dev first
- [ ] Container reports `healthy`
- [ ] **No** Nginx change, **no** compose change, **no** env-var change

**Browser verification (dev, before prod)**
- [ ] View source on `/login` → `<link href="/css/app-<32 hex>.css">`
- [ ] DevTools Network: the CSS request returns `200` with `Cache-Control: max-age=2592000`
- [ ] Hard-reload, then normal reload → CSS served from cache (caching still works)
- [ ] Confirm **no** `;jsessionid=` in any asset URL
- [ ] Log in, click through Pipeline / My Work / Reviews / Reports / Admin → no console errors, styling correct, icons render
- [ ] Confirm login, workflow transitions, and notifications behave normally

**Prod**
- [ ] Repeat on `/opt/kcpc-prod`
- [ ] Verify with a real user's browser **without** clearing cache — this is the actual acceptance test

**Rollback**
- [ ] `deploy/scripts/rollback.sh` is unchanged and applies normally (see §14)

---

## 14. Rollback

Rolling back to the previous image tag is **clean and requires no special handling**.

| Aspect | Behavior on rollback |
|---|---|
| **Asset URLs** | JSPs revert to `${pageContext.request.contextPath}/css/app.css`. Pages render unversioned URLs again |
| **Hashed URLs in browser caches** | Become harmless orphans. Nothing references them; they expire naturally within 30 days |
| **Browser cache behavior** | HTML is `no-cache`, so every user immediately receives the reverted HTML with unversioned URLs on their next page load |
| **Do users need to clear cache?** | **No** — but the *original staleness bug returns*, since `/css/app.css` is unversioned again. A user holding a cached copy may see old CSS until it expires. This is the pre-rollback status quo, not a new problem |
| **Database rollback** | **None required.** No migrations were added or modified. `restore-postgres.sh` is not needed |
| **Session tracking** | Reverts to Tomcat's default (cookie + URL). The `;jsessionid=` fallback becomes possible again — harmless without `<c:url>` on assets |
| **Nginx / Docker / GCP** | Nothing to revert — none changed |
| **Procedure** | Standard `deploy/scripts/rollback.sh`. `deploy.sh` also auto-rolls-back on healthcheck failure |

**Rollback risk: LOW.** Code-only, no data migration, no infrastructure state.

---

## 15. Final executive verdict

### Q1. Will cache-busting work after production deployment?

**YES.** Verified end-to-end on a live Docker + Nginx stack that mirrors production: changing CSS changed the URL, unchanged JS kept its URL, and a browser cache that was *never cleared* fetched the new file after redeployment. The mechanism depends only on classpath content, so it behaves identically through Source → WAR → Docker → GCP.

### Q2. Will any production business data be modified?

**NO.** Zero Flyway migrations added or modified, zero schema changes, zero data writes, zero new DB queries. The change set is 1 YAML file, 1 Java config class, 38 JSPs, and 3 test files. **Production data impact: NONE.**

### Q3. Will production become slower?

**NO.** One MD5 per static file, memoized for the JVM's lifetime (`chain.cache: true` under the `docker` profile, which every deployed environment uses). Per-page cost is 5–9 hash-map lookups. Net effect is a small **improvement**, because working long-lived caching eliminates repeat asset downloads.

### Q4. Does production Nginx need to change?

**NO.** All three Nginx configs are byte-identical to HEAD. Hashed filenames still end in `.css`/`.js`, so they match the existing location regex and receive `expires 30d` and gzip exactly as before — verified live.

### Q5. Does Docker configuration need to change?

**NO.** `Dockerfile` and all three compose files are unchanged. The one requirement is already satisfied: `SPRING_PROFILES_ACTIVE=docker` is set in local compose, `deploy/dev`, and `deploy/prod`.

### Q6. Does GCP configuration need to change?

**NO.** No infrastructure, load balancer, env-var, or deploy-script change. `deploy.sh` and `rollback.sh` are unchanged.

### Q7. Does the production DB need any change?

**NO.** See Q2. *(Separately: verify the target VM has no pre-existing Flyway checksum drift — unrelated to this change, but it would block any deployment.)*

### Q8. Do users need to clear browser cache?

**NO.** This is the defect being fixed. HTML is served `no-cache, no-store`, so every user receives fresh HTML containing new hashed URLs, and a URL the browser has never seen cannot be served from cache. Demonstrated with a cache that was never cleared across a redeploy.

### Q9. What exact files changed?

**43 files: 252 insertions(+), 147 deletions(-).**

**Production code and configuration (2):**
- `src/main/resources/application.yml`
- `src/main/java/com/kcpc/mkt/web/mvc/WebMvcConfig.java`

**Views (38)** — `href`/`src` attribute values only, 142 substitutions:

`fragments/nav.jsp` · `admin-business-roles.jsp` · `admin-catalogue.jsp` · `admin-categories.jsp` · `admin-marks.jsp` · `admin-permissions.jsp` · `admin-user-detail.jsp` · `admin-users.jsp` · `admin-users-import.jsp` · `admin-users-import-preview.jsp` · `admin-users-import-result.jsp` · `audit-history.jsp` · `change-password.jsp` · `deliverable-detail.jsp` · `edit-task-detail.jsp` · `export.jsp` · `forgot-password.jsp` · `home.jsp` · `idea-detail.jsp` · `idea-queue.jsp` · `idea-submit.jsp` · `login.jsp` · `my-ideas.jsp` · `my-performance.jsp` · `my-shoots.jsp` · `my-work.jsp` · `my-work-history-detail.jsp` · `notifications.jsp` · `pipeline.jsp` · `publish-task-detail.jsp` · `reports-admin-actions.jsp` · `reports-delayed.jsp` · `reports-kpi-console.jsp` · `reports-team-kpis.jsp` · `reports-workload.jsp` · `reset-password.jsp` · `reviews.jsp` · `shoot-task-detail.jsp`

**Tests (3)** — assertions made version-tolerant:
- `BrandLogoTest.java` · `LoginPasswordVisibilityToggleTest.java` · `PipelineAjaxPartialTest.java`

**Unchanged:** all Nginx configs · `Dockerfile` · all compose files · `deploy/scripts/*` · `pom.xml` · all Flyway migrations · `app.css` · all JS/image/icon assets · `SecurityConfig.java` · every controller, service, entity, and repository.

### Q10. Is it safe to deploy?

> # APPROVED WITH CONDITIONS

The implementation is correct, verified end-to-end, and carries no database, Nginx, Docker, GCP, authentication, or performance risk. The regression evidence is unambiguous: **613 tests, 605 passed, zero new failures**, with every failure reproducing identically on pristine HEAD.

**Conditions — neither blocks deployment:**

1. **Accept the latent JS context-path coupling (§12).** Four JS files locate their own `<script>` tag by exact filename, which cache-busting changes. At the root context path this is **provably a no-op** — both old and new code return `''`. It becomes a real bug only if the app is ever moved to a non-root context path. Track it as follow-up; do not let it hold up this deployment.

2. **Consider consolidating the duplicated session-tracking configuration (§5).** Cookie-only tracking is enforced both in `application.yml` and by a `ServletContextInitializer` bean. They set the same value and cannot conflict; keeping both is defensible for external-container deployment. Tidiness, not risk.

**Verified before deploying:**
- Confirm `SPRING_PROFILES_ACTIVE=docker` on the target (already true in all three compose files) — without it the resolver cache is off and hashes are recomputed per request.
- Confirm the target VM's database has no Flyway checksum drift (unrelated to this change; currently present on the local container).
- Deploy to dev first and complete the §13 browser checklist before prod.

**What remains genuinely unverified:** client-side JavaScript behavior has no automated coverage in this project (`src/test/js` is not wired into the Maven build), so the manual browser click-through in §13 is the only check on the four modules discussed in §12. Perform it on dev before promoting to prod.
