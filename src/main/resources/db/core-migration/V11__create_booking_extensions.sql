create table booking_extensions (
    id uuid primary key,
    booking_id uuid not null references bookings(id) on delete cascade,
    original_check_out_date date not null,
    requested_check_out_date date not null,
    added_nights integer not null check (added_nights > 0),
    original_booking_amount numeric(12, 2) not null check (original_booking_amount >= 0),
    additional_amount numeric(12, 2) not null check (additional_amount > 0),
    resulting_total_amount numeric(12, 2) not null check (resulting_total_amount >= 0),
    currency varchar(3) not null,
    status varchar(30) not null,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    check (requested_check_out_date > original_check_out_date)
);

create index booking_extensions_booking_status_idx on booking_extensions(booking_id, status, expires_at);

alter table payments add column booking_extension_id uuid references booking_extensions(id);
create index payments_booking_extension_id_idx on payments(booking_extension_id);
