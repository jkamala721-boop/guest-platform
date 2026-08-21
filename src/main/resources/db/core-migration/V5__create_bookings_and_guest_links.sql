create table bookings (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    property_id uuid not null references properties(id),
    guest_id uuid not null references guests(id),
    check_in_date date not null,
    check_out_date date not null,
    total_amount numeric(12, 2) not null check (total_amount >= 0),
    currency varchar(3) not null,
    status varchar(40) not null,
    notes varchar(2000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    check (check_out_date > check_in_date)
);

create index bookings_host_id_created_at_idx on bookings(host_id, created_at desc);
create index bookings_property_dates_status_idx on bookings(property_id, check_in_date, check_out_date, status);
create index bookings_guest_id_idx on bookings(guest_id);

create table guest_links (
    id uuid primary key,
    booking_id uuid not null references bookings(id) on delete cascade,
    token_hash varchar(64) not null unique,
    state varchar(40) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index guest_links_booking_id_idx on guest_links(booking_id);
create index guest_links_token_hash_idx on guest_links(token_hash);
