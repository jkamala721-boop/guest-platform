create table host_payout_settings (
    host_id uuid primary key references hosts(id),
    payout_method varchar(30) not null,
    settlement_bank_code varchar(80) not null,
    account_number_last4 varchar(4) not null,
    account_name varchar(160) not null,
    paystack_subaccount_code varchar(100) not null unique,
    status varchar(30) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table payments add column processor_fee numeric(12, 2);
alter table payments add column host_payout_amount numeric(12, 2);
alter table payments add column hostvero_net_amount numeric(12, 2);

alter table payments add constraint payments_processor_fee_nonnegative
    check (processor_fee is null or processor_fee >= 0);
alter table payments add constraint payments_host_payout_amount_nonnegative
    check (host_payout_amount is null or host_payout_amount >= 0);
