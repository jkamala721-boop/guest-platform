# Phase 2 Stage 2: Property photographs and guest gallery

Status: subsequently approved for local implementation. This planning baseline is
preserved; see [implementation details](PROPERTY_PHOTOS_STAGE2.md) and
[verification](STAGE2_VERIFICATION.md) for current behavior and remaining release gates.

Pre-implementation proposal, November 14, 2026. No migrations, application changes,
production Storage operations, environment changes, deploys or merges performed.

## Baseline and isolation

Fetched GitHub main: `36dd9b0e3f042e2c39e1692663558d6904755e50`.
Development branch: `feat/property-photos-stage2`.
Worktree: `/tmp/hostvero-property-photos-stage2`.
The earlier checkout and uncommitted Stage 1 readiness document are preserved.

Inspected Property/PropertyService/PropertyController, PropertyOfferService and
controller, frontend API helper and offer/gallery insertion points, security CSP,
service-worker behavior, existing Flyway layout and deployment configuration.
No existing property-photo model, storage adapter or gallery was found on main.

- PropertyController uses CurrentHost; PropertyRepository scopes ownership by host.
- PropertyOfferService validates token hash, expiry/revocation and active host/property.
  Reuse that validation through a narrow service method; do not duplicate it.
- Properties are deactivated rather than hard-deleted. Do not cascade photo deletion
  on deactivation; deny guest access and allow an owner to manage/remove photos.
- `offers.js` renders the public offer and date form; add the gallery above the form.
  Extend the host property detail/editor with management, without rewriting app.js.
- `api.js` assumes every request body is JSON. Add FormData support or a small
  dedicated upload helper; keep browser-generated multipart boundaries.
- CSP currently allows only self/data images. Permit the exact configured Supabase
  HTTPS image origin; no wildcard. Browser API calls remain same-origin.
- Service worker excludes offer/guest/API data. Do not cache signed gallery URLs or
  image responses in the application service worker.

## Production identity and bucket: blocked

Verified Stage 1 production logs identify PostgreSQL 17.6 through the shared
`aws-0-eu-north-1.pooler.supabase.com:5432` endpoint. This host alone cannot identify
a project. Render's available connector cannot list environment variables.

Fresh Supabase discovery still lists only one INACTIVE project in eu-west-1.
It is not confirmed as production. No queries or bucket operations were sent to it.
Therefore **bucket existence is unknown**, not absent.

Needed: non-secret production project reference confirmed against Render's datasource,
and read-only access to that project. Do not paste database passwords, service-role
keys or connection strings. Once confirmed, inspect only bucket metadata, e.g.:

```sql
select id, name, public, file_size_limit, allowed_mime_types
from storage.buckets where id = 'hostvero-property-photos';
```

If it exists, verify private status, limits and relevant Storage policies. Do not
change an existing bucket automatically. Do not resume any inactive database.

## Minimal design proposed for approval

Use private bucket `hostvero-property-photos`. All uploads pass through Spring Boot;
no browser service-role key, anonymous upload policy or guest account is needed.
Backend uses a small Supabase Storage REST adapter through the existing Java HTTP
stack, with timeouts, bounded retries and safe error mapping. No new application stack.

### Schema

One `property_photos` table:

- UUID id; property_id FK; unique object_prefix using random IDs (no guest data or
  original filename); lifecycle state UPLOADING / READY / DELETE_PENDING.
- sort_order integer; is_cover boolean default false; created_at and updated_at.
- thumbnail/display MIME type, width, height and byte length (nullable while uploading).
  Fixed variant names derive from object_prefix; store no signed URLs.
- Index `(property_id, state, sort_order)`; partial unique index on property_id
  where is_cover and state = READY. Cover can reference only a READY photo.
- Check nonnegative sizes/order and valid lifecycle metadata. RLS enabled and Data
  API privileges revoked from anon/authenticated, following existing server-only tables.

Provisional next migrations are V34 (metadata) / V35 (PostgreSQL protection), only
if still unused at implementation time. Use repository Flyway conventions; do not
edit V1–V33 or write Storage's internal object metadata directly.

At most 15 READY plus UPLOADING photos per property. Enforce under the existing
property row lock; finalize, delete, cover selection and reorder use the same lock.
Do not rely on a race-prone count outside the lock. Reorder accepts the exact current
READY ID set; stale/missing/duplicate/foreign IDs return conflict/validation errors.
Lock only short database phases, never remote uploads or image decoding.

### API

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/properties/{id}/photos` | Owner-only ordered READY list with signed variants and expiry |
| POST | `/api/properties/{id}/photos` | Owner-only multipart, one image per request; returns metadata |
| DELETE | `/api/properties/{id}/photos/{photoId}` | Owner-only logical removal and queued physical cleanup |
| PUT | `/api/properties/{id}/photos/order` | Owner-only complete ordered photoIds array |
| PUT | `/api/properties/{id}/photos/cover` | Owner-only photoId; exactly one cover when READY photos exist |
| GET | `/api/public/offers/{token}/photos` | Validate usable offer, return property's ordered gallery |

Host authorization always checks property ownership and operational access for
mutations, and verifies each photo belongs to that property. Reuse host-session and
cookie-origin protections. Public gallery inherits public token rate limits.
No unauthenticated property-ID gallery endpoint. An offer token permits gallery
viewing before registration/payment without exposing location/access instructions.
Existing guest-link galleries, if approved, use a separate token-scoped GET endpoint
through GuestLinkService's existing usable-link validation; never arbitrary property IDs.

### Upload and optimization

Proposed limits: JPEG and PNG input, 10 MiB maximum, 20 megapixels maximum,
10,000 px maximum per axis; one image processed at a time per instance initially.
Reject SVG, GIF/animation, HEIC and WebP initially with clear messages. HEIC support
is a product decision for iPhone uploads, not silently promised by the file picker.

Verify declared type, signature and decoder format; inspect dimensions before
full decoding and enforce bounds. Reject malformed, unsupported and multi-frame
content. Decode and re-encode pixels; do not trust filenames or Content-Type alone.
Apply EXIF orientation before stripping EXIF/GPS and other source metadata. A pinned
Java metadata library may be needed for orientation; review this dependency first.

Generate a 480 px thumbnail and a 1920 px display image (long edge, preserve aspect,
never upscale), JPEG quality about 0.82–0.85, flatten transparency on white. Originals
are not retained. Fullscreen uses the display variant; no paid transformations.
Bound upload/processing concurrency to protect the small Render instance; return
a retryable busy response instead of risking heap exhaustion. Verify peak memory
with max-size fixtures before selecting production JVM/instance limits.

### Storage lifecycle and failures

1. Authorize and validate limits, reserve an UPLOADING row under property lock.
2. Optimize locally using bounded memory/temp space; upload both immutable variants
   to `{propertyId}/{photoId}/thumb.jpg` and `display.jpg`, with overwrite disabled.
3. Reauthorize/finalize in a short transaction, mark READY and append order; first
   photo becomes cover. Only READY rows may be listed or signed.
4. On failure, persist DELETE_PENDING when possible and remove uploaded variants.
   Stale UPLOADING rows are claimed for cleanup only after a conservative timeout
   longer than the entire bounded upload operation; finalization must check state.
5. Delete hides the photo atomically, chooses the first remaining cover if needed,
   and records DELETE_PENDING. A retry worker removes objects through Storage API
   and deletes metadata only after confirmed cleanup. Missing objects are idempotent.
6. Database and Storage are not one transaction. Retain failed cleanup rows/retry
   diagnostics; reconcile immutable object prefixes after crashes/DB failures using
   a conservative age threshold and a fresh metadata check, avoiding active uploads.
   Storage cleanup is implemented/tested locally first, never run during planning.

Signed URLs: propose 120-second expiry, capped to remaining offer/link lifetime.
Return expiresAt; refresh through the same authorized endpoint on expiry once,
without an infinite retry loop. Use no-store on gallery JSON and conservative
private/no-store object cache control; no token logging or referrer leakage.
An issued URL is a bearer capability usable until expiry even after token revocation;
deleting an image does not revoke copies already downloaded. No promise of instant
revocation of an issued URL or of browser/screenshot copies.

### Frontend

Responsive cover + thumbnail grid before booking dates; lazy-load non-cover images,
use thumbnail/display srcset, explicit aspect ratios and an empty state. Gallery
failures must not block date selection or booking. Fullscreen overlay supports
touch navigation, visible previous/next/close, Escape/arrow keys, focus trapping
and restoration, and accessible property/photo-number labels. Use explicit reorder
buttons on mobile/keyboard; drag-and-drop can remain optional. No unrelated editor.

## Render configuration proposal

- Backend-only `SUPABASE_URL=https://<confirmed-project-ref>.supabase.co` and
  `SUPABASE_SERVICE_ROLE_KEY` (or an agreed supported server-secret credential).
  These are separate from JDBC credentials. Never expose via HTML/JS, logs or Git.
- `PROPERTY_PHOTOS_ENABLED=false` until release prerequisites are verified;
  `PROPERTY_PHOTOS_BUCKET=hostvero-property-photos`, max count 15, input bytes,
  pixel/dimension limits, signed TTL and bounded Storage timeouts configurable
  with defaults matching this plan. No automatic bucket creation at startup.
- Spring multipart max-file-size 10 MiB, max-request-size slightly larger for
  multipart overhead, and a streaming/body cap before accepting excess content.
  Check any proxy limits. Use ephemeral temporary files only and delete promptly.
- Derive/validate exact Storage origin for CSP img-src. No wildcard connect-src,
  frontend Storage credential or direct-browser upload CORS setup is needed.
- No persistent disk/new worker service proposed. A small scheduled cleanup worker
  runs within the backend with database coordination; do not depend on free-instance
  uptime for correctness. Confirm whether measured memory requires a plan change
  before approval; no automatic infrastructure upgrade.
- Set `java.awt.headless=true` for Java image processing. Document Storage object
  backup alongside metadata backup: a PostgreSQL dump alone does not restore files.

## Required approvals and verification before implementation/release

1. Confirm production project reference/read-only access so bucket existence and
   settings can be checked. This is the current discovery blocker.
2. Approve input limits/formats, JPEG variants/no-original retention, 120-second
   signed lifetime/revocation limit, and whether existing guest-link pages also get
   the gallery (recommended, only while their links remain usable).
3. Approve the local schema/API/lifecycle proposal before writing migrations.
4. Separately approve provisioning/configuring the private production bucket and
   backend credential/Render settings after the actual target is established.
   Those actions, migration execution and deployment remain prohibited now.

Implementation validation: PostgreSQL upgrade/constraints and concurrent 15-photo
enforcement; owner/cross-owner/token expiry; malformed/oversized/oriented images;
Storage timeout/partial upload/delete retry; signed URL renewal; cover/reorder races;
desktop/mobile/fullscreen acceptance; existing booking/payment regressions. Use
isolated storage fixtures and database, never production writes for tests.

References: [Supabase private buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals),
[signed URLs](https://supabase.com/docs/reference/javascript/storage-from-createsignedurl).
