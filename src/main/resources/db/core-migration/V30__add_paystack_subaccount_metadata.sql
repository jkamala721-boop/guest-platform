alter table host_payout_settings add column paystack_subaccount_id bigint;
alter table host_payout_settings add column paystack_subaccount_domain varchar(10);
alter table host_payout_settings add column paystack_subaccount_active boolean;
alter table host_payout_settings add column paystack_subaccount_verified boolean;

alter table payments add column provider_channel varchar(40);

