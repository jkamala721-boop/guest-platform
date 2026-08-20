-- The Spring Boot application accesses these tables through its server-side
-- PostgreSQL role.  The Supabase Data API roles must not access them directly.
alter table public.hosts enable row level security;
alter table public.properties enable row level security;
alter table public.host_sessions enable row level security;

revoke all on table public.hosts from anon, authenticated;
revoke all on table public.properties from anon, authenticated;
revoke all on table public.host_sessions from anon, authenticated;
