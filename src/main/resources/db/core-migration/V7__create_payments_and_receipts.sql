create table payments (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    booking_id uuid not null references bookings(id),
    provider varchar(20) not null,
    provider_reference varchar(200) not null,
    provider_event_id varchar(200) unique,
    amount numeric(12, 2) not null check (amount >= 0),
    currency varchar(3) not null,
    status varchar(20) not null,
    failure_reason varchar(500),
    paid_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint payments_provider_reference_key unique (provider, provider_reference)
);

create index payments_host_id_created_at_idx on payments(host_id, created_at desc);
create index payments_booking_id_created_at_idx on payments(booking_id, created_at desc);

create table receipts (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    booking_id uuid not null references bookings(id),
    payment_id uuid not null unique references payments(id),
    receipt_number varchar(40) not null unique,
    amount numeric(12, 2) not null check (amount >= 0),
    currency varchar(3) not null,
    issued_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index receipts_host_id_issued_at_idx on receipts(host_id, issued_at desc);
create index receipts_booking_id_idx on receipts(booking_id);
