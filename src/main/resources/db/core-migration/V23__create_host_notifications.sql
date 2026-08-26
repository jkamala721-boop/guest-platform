create table host_notifications (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    booking_id uuid not null references bookings(id) on delete cascade,
    payout_id uuid references host_payouts(id) on delete cascade,
    type varchar(40) not null,
    event_key varchar(160) not null,
    status varchar(20) not null,
    delivery_detail varchar(500),
    sent_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint host_notifications_event_key_key unique (event_key)
);

create index host_notifications_status_created_at_idx on host_notifications(status, created_at);
create index host_notifications_booking_type_idx on host_notifications(booking_id, type);

