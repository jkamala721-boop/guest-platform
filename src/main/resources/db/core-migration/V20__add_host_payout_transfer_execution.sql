alter table host_payouts add column transfer_code varchar(100);
alter table host_payouts add column provider_status varchar(40);
alter table host_payouts add column completed_at timestamp with time zone;
alter table host_payouts add column last_attempt_at timestamp with time zone;
alter table host_payouts add column attempt_count integer not null default 0;
alter table host_payouts add column retryable boolean not null default false;
alter table host_payouts add constraint host_payouts_transfer_code_key unique (transfer_code);
