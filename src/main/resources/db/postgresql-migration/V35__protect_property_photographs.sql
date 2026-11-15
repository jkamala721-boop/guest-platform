alter table public.property_photos enable row level security;
revoke all on table public.property_photos from anon, authenticated;
create unique index property_photos_one_cover_idx on public.property_photos(property_id)
    where is_cover and state = 'READY';
