alter table public.property_offers enable row level security;
revoke all on table public.property_offers from anon, authenticated;
