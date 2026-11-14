create table property_offers (
    id uuid primary key,
    property_id uuid not null references properties(id),
    token_hash varchar(64) not null unique,
    nightly_price numeric(12,2) not null check (nightly_price > 0),
    currency varchar(3) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    check (expires_at > created_at)
);
create index property_offers_property_created_idx on property_offers(property_id, created_at);
alter table bookings add column offer_id uuid references property_offers(id);
alter table bookings add column checkout_hold_expires_at timestamp with time zone;
alter table bookings add column reservation_confirmed_at timestamp with time zone;
alter table bookings add column payment_deadline_at timestamp with time zone;
alter table bookings add constraint bookings_offer_deadlines_check check (
    (offer_id is null and checkout_hold_expires_at is null and reservation_confirmed_at is null and payment_deadline_at is null)
    or (offer_id is not null and checkout_hold_expires_at is not null and payment_deadline_at is not null)
);
create index bookings_offer_idx on bookings(offer_id);
create index bookings_offer_deadline_idx on bookings(payment_deadline_at, status);
create index bookings_offer_hold_idx on bookings(checkout_hold_expires_at, status);
alter table payments add column reservation_resolution_required_at timestamp with time zone;
