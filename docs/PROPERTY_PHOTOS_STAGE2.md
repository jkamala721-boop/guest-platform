# Phase 2 Stage 2 — Property photographs and guest gallery

Implemented locally on `feat/property-photos-stage2` from main `36dd9b0`.
No production Storage or Render changes, production migrations, pushes, merges or
production deployments were performed. See [the approved plan](PROPERTY_PHOTOS_STAGE2_PLAN.md).

## Behavior

Hosts select **Photographs** in Properties to upload a JPEG/PNG, change order with
Earlier/Later, select a cover or delete an image. Up to 15 READY/in-progress images
are permitted per property. The first image is the cover; deleting the cover selects
the first remaining image. Deactivating a property does not delete its images.
Management checks authenticated ownership and operational access on each mutation.

Guests see the cover/thumbnail gallery above date selection on `/offer/{token}`
and on an existing usable `/guest/{token}`. No account, app or payment is needed to
view it. Fullscreen has close/previous/next, Escape/arrow keys, native modal focus
containment and restoration, and swipe navigation. Signed images are not saved to
local/session storage or cached by the app's service worker. Galleries failing to
load do not prevent date selection, registration or existing payment behavior.

The gallery shows current property photographs, not a historical booking snapshot.
Changing photos updates all usable property offer and booking-link galleries.

## Validation and optimization

- Only matching JPEG/PNG declared MIME, signature and decoder content are accepted.
- Maximum 10 MiB, 20,000,000 source pixels, 10,000 pixels per axis. Empty, malformed,
  multi-image and animated PNG uploads are rejected. HEIC/WebP/GIF/SVG are unsupported.
- Dimensions are inspected before decoding; subsampling bounds the decoded image.
  One upload is processed per application instance; busy requests return retryable 503.
- EXIF orientation (including mirrored orientations) is applied through pinned
  `metadata-extractor:2.19.0`; re-encoding removes original EXIF/GPS metadata.
- JPEG output: 480 px thumbnail and 1920 px display maximum long edge, aspect ratio
  preserved, no upscaling, 0.84 JPEG quality, transparency flattened onto white.
  Fullscreen uses the display image. Original files are not stored.
- Multipart files spool to temporary storage and are cleaned by the servlet lifecycle.
  No persistent filesystem disk or paid image transformations are required.

## Metadata and consistency

V34 adds `property_photos` with a property FK, random immutable object prefix,
UPLOADING/READY/DELETE_PENDING state, ordering, cover, variant dimensions/byte sizes
and timestamps. JPEG MIME is fixed for both variants. V35 adds RLS, denies Data API
roles and creates a partial unique single-cover index. Earlier migrations are unchanged.

Metadata uses JdbcTemplate within the existing JPA transaction manager, so the
existing property-row lock serializes capacity checks, publication, cover and order
changes. Remote I/O and decoding happen outside those locks. Concurrent application
instances cannot create more than 15 active slots; local semaphores bound memory.
Reorder must include each current READY ID exactly once, otherwise returns 409.

Upload reserves metadata before writing `{propertyId}/{photoId}/thumb.jpg` and
`display.jpg`. Only after both uploads succeed does a short transaction publish the
READY row. If processing/storage/finalization fails, no broken READY photo is
published; a reserved row is marked DELETE_PENDING or remains reclaimable UPLOADING
if the database itself is unavailable. Objects are immutable, with overwrite disabled.
The API returns the new ID only; subsequent gallery loading is independent of upload.

Delete immediately hides the row, then schedules physical removal. Cleanup waits
five minutes after logical deletion to avoid racing an in-flight/timed-out upload.
UPLOADING rows older than one hour are recovered. Cleanup deletes both variants
through Supabase's bulk Storage API, tolerates already absent objects, and removes
metadata only after Storage reports success. Errors retain metadata for later retry;
a safe warning is logged without paths, URLs, filenames, provider bodies or keys.
Each pass handles at most 20 candidates; it retries every 60 seconds by default.
A single dedicated in-process cleanup thread prevents Storage delays from blocking
existing booking/notification/payout schedulers; overlapping passes are skipped.
A free Render instance sleeping delays cleanup but does not expose pending photos.

Durable rows precede object creation, so ordinary upload failures and process crashes
retain a reconciliation record. This is not a general bucket-wide orphan scanner:
a database restore or manual removal of metadata can orphan files and requires an
approved reconciliation procedure. Do not directly delete `storage.objects` rows.

## Signed URLs

All management and signing go through the backend. Public photo APIs validate the
existing offer/guest token, property and host state. No public property-ID endpoint
or anonymous upload policy is added. Public photo calls use the existing token rate
limiter. Signing happens in a single batch for both variants to bound provider I/O.

URLs expire after at most 120 seconds, shortened near offer/guest expiration and
unpaid offer deadlines. The provider's 20-second request budget is subtracted when
capping to authorization expiry; no new image URLs are issued in the final 20 seconds.
The response contains `expiresAt`; frontend refreshes before opening stale fullscreen
images and retries a failed image once through the authorized endpoint. Gallery JSON
has `Cache-Control: no-store`; uploaded objects request private/no-store cache control.

An already-issued URL remains a bearer capability until expiration even if an offer
is revoked in that interval. Downloaded/browser-held copies cannot be revoked.
The backend returns only URLs under the configured exact origin and checks signed
paths against requested objects. Images use no-referrer. Provider failures return
safe retryable `PHOTOS_UNAVAILABLE` responses, without disrupting booking APIs.

## API

| Method | Route | Payload / result |
| --- | --- | --- |
| GET | `/api/properties/{id}/photos` | Owner gallery `{enabled, photos}` |
| POST | `/api/properties/{id}/photos` | Multipart `file`; 201 `{id}` |
| PUT | `/api/properties/{id}/photos/order` | `{photoIds: [UUID, ...]}`; 204 |
| PUT | `/api/properties/{id}/photos/cover` | `{photoId: UUID}`; 204 |
| DELETE | `/api/properties/{id}/photos/{photoId}` | Logical removal; 204 |
| GET | `/api/public/offers/{token}/photos` | Token-scoped gallery |
| GET | `/api/public/guest/{token}/photos` | Usable booking-link gallery |

Photo fields: id, sortOrder, cover, thumbnailUrl, displayUrl, thumbnailWidth,
thumbnailHeight, displayWidth, displayHeight and expiresAt. Original filenames,
Storage credentials and internal lifecycle rows are never included. Disabled
features return an empty gallery with `enabled:false`; mutations return 503.

## Configuration and remaining deployment dependencies

No production configuration was changed. Backend configuration:

| Environment variable | Default / requirement |
| --- | --- |
| `PROPERTY_PHOTOS_ENABLED` | false; keep disabled until prerequisites pass |
| `SUPABASE_URL` | Empty; when enabled, required exact HTTPS project origin, no path/query |
| `SUPABASE_SERVICE_ROLE_KEY` | Empty; required backend-only secret when enabled |
| `PROPERTY_PHOTOS_CLEANUP_DELAY_MS` | 60000; positive milliseconds |

Bucket is fixed to `hostvero-property-photos`. Core accepted limits/TTL are fixed to
the approved values. Multipart limits are 10 MB per file and 11 MB per request.
Spring Boot defaults to headless AWT; Java image processing needs no GUI. Runtime
memory under maximum-size real camera images must be checked on a staging instance
before enabling on the small production instance; subsampling/concurrency limit
memory, but tests are not evidence of production heap/latency capacity.

CSP adds only the configured Storage origin to `img-src` when enabled; `connect-src`
remains self. Uploads and gallery refresh use the existing backend, so browser Storage
keys, direct-upload CORS, new services and a persistent disk are unnecessary.

Production project identity and bucket existence remain unverified. The only
connector-visible project is inactive in eu-west-1; Stage 1 production used a shared
eu-north-1 session pooler. Do not assume these are the same project or resume it.
Before release, confirm the actual project and privately inspect the bucket/policies.
No startup bucket creation or policy mutation exists in this implementation.

Separate approval is still needed to provision/verify a private production bucket,
set backend credentials/enable flag in Render, apply V34/V35 and deploy. Keep objects
server-only and set bucket restrictions appropriate for the optimized JPEG outputs.
Do not rotate existing encryption/fingerprint credentials as part of this feature.
Verify a database backup and an independent Storage object backup/restore procedure:
a PostgreSQL dump contains metadata, not the photograph files themselves. Main
currently auto-deploys on commit; do not push/merge this branch before release approval.

## Verification and manual acceptance

Automated tests use disposable PostgreSQL and mocked Storage only; no live Supabase
requests, guest notifications or provider payments. Test results and changed-file
inventory are recorded in [Stage 2 verification](STAGE2_VERIFICATION.md).

Before production enablement, on desktop Chrome/Firefox and real Android Chrome /
iPhone Safari with an isolated staging property:

1. Upload landscape, portrait EXIF, mirrored and transparent JPEG/PNG samples; check
   rotation, cover, thumbnail/fullscreen appearance. Reject unsupported/oversized files.
2. Upload to 15; verify the 16th is rejected. Reorder with keyboard/mobile buttons,
   change cover, refresh, and confirm persisted order/cover. Another host cannot edit.
3. Open a usable offer while signed out: gallery precedes dates. Open an existing
   guest link: same current gallery. Zero-photo properties still allow booking.
4. Use full screen, arrows, swipe, Escape/Close and keyboard focus restoration;
   verify narrow portrait/landscape layouts and 200% zoom.
5. Leave a page open beyond two minutes; open fullscreen/scroll lazy images and
   verify refresh. Expired/revoked offer/guest links cannot obtain fresh images.
6. Simulate Storage outage/partial upload/deletion: no broken active photo, booking
   still works, delete hides immediately and remote cleanup eventually retries.
7. Verify an existing installed/cached app updates to shell v3 and `photos.js` loads.

No browser runtime is available in this environment, so visual/touch acceptance and
real development-bucket interoperability remain deployment prerequisites.
