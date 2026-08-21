alter table public.booking_extensions enable row level security;

revoke all on table public.booking_extensions from anon, authenticated;
