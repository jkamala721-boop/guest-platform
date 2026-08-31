alter table host_payout_settings drop constraint host_payout_settings_recipient_code_key;

create index idx_host_payout_settings_recipient_code
    on host_payout_settings (paystack_recipient_code);
