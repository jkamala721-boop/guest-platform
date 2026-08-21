-- Booking and guest-link data is accessible only through the server-side
-- Spring Boot database role, never directly through Supabase Data API roles.
alter table public.bookings enable row level security;
alter table public.guest_links enable row level security;

revoke all on table public.bookings from anon, authenticated;
revoke all on table public.guest_links from anon, authenticated;
