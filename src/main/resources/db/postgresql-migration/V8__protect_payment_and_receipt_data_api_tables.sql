-- Payment and receipt records are server-side financial data. Supabase Data API
-- roles must not access them directly; the Spring Boot database role remains the
-- only application data access path.
alter table public.payments enable row level security;
alter table public.receipts enable row level security;

revoke all on table public.payments from anon, authenticated;
revoke all on table public.receipts from anon, authenticated;
