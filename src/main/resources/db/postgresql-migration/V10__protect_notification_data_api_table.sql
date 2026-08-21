-- Notification history is server-side operational data. Supabase Data API roles
-- must not access it directly; the Spring Boot database role remains the only
-- application data access path.
alter table public.notifications enable row level security;

revoke all on table public.notifications from anon, authenticated;
