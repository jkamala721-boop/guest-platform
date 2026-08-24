alter table payments add column booking_amount numeric(12, 2) not null default 0;
alter table payments add column service_fee numeric(12, 2) not null default 0;

update payments set booking_amount = amount where booking_amount = 0;

alter table payments add constraint payments_booking_amount_nonnegative check (booking_amount >= 0);
alter table payments add constraint payments_service_fee_nonnegative check (service_fee >= 0);
