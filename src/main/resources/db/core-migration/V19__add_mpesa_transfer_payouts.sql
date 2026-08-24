alter table host_payout_settings alter column settlement_bank_code drop not null;
alter table host_payout_settings alter column account_number_last4 drop not null;
alter table host_payout_settings alter column account_name drop not null;
alter table host_payout_settings alter column paystack_subaccount_code drop not null;
alter table host_payout_settings add column paystack_recipient_code varchar(100);
alter table host_payout_settings add column mpesa_phone_last4 varchar(4);
alter table host_payout_settings add column mpesa_phone_fingerprint varchar(64);
alter table host_payout_settings add constraint host_payout_settings_recipient_code_key unique (paystack_recipient_code);
alter table host_payout_settings add constraint host_payout_settings_destination_check check (
    (payout_method = 'BANK_ACCOUNT' and settlement_bank_code is not null and account_number_last4 is not null
        and account_name is not null and paystack_subaccount_code is not null)
    or (payout_method = 'MPESA' and mpesa_phone_last4 is not null and paystack_recipient_code is not null)
);

alter table payments add column payout_method varchar(30);
alter table payments add column payout_destination_reference varchar(100);

create table host_payouts (
    id uuid primary key,
    payment_id uuid not null unique references payments(id),
    host_id uuid not null references hosts(id),
    payout_method varchar(30) not null,
    recipient_code varchar(100) not null,
    provider_reference varchar(64) not null unique,
    amount numeric(12, 2) not null,
    currency varchar(3) not null,
    status varchar(30) not null,
    failure_reason varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint host_payouts_amount_nonnegative check (amount >= 0)
);
