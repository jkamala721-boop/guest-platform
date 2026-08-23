alter table guests add column active boolean not null default true;
create index guests_host_active_created_at_idx on guests(host_id, active, created_at desc);

alter table notifications drop constraint notifications_booking_type_key;
alter table notifications add column subject varchar(200);
alter table notifications add column message varchar(4000);
create index notifications_booking_type_idx on notifications(booking_id, type);
