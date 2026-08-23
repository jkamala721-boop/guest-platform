alter table bookings add column guest_access_policy varchar(30) not null default 'AFTER_PAYMENT';
