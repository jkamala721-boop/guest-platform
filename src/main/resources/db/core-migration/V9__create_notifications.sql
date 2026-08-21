create table notifications (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    booking_id uuid not null references bookings(id) on delete cascade,
    guest_id uuid not null references guests(id),
    type varchar(60) not null,
    channel varchar(20) not null,
    status varchar(20) not null,
    scheduled_at timestamp with time zone not null,
    sent_at timestamp with time zone,
    extension_available boolean,
    delivery_detail varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint notifications_booking_type_key unique (booking_id, type)
);

create index notifications_host_scheduled_at_idx on notifications(host_id, scheduled_at desc);
create index notifications_booking_scheduled_at_idx on notifications(booking_id, scheduled_at desc);
create index notifications_due_idx on notifications(status, scheduled_at);
