-- Guest PII is accessible only through the server-side Spring Boot database role.
alter table public.guests enable row level security;
revoke all on table public.guests from anon, authenticated;
