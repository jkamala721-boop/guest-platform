alter table properties add column access_method varchar(40);
alter table properties add column access_code_ciphertext varchar(1024);
alter table properties add column access_location_instructions varchar(5000);
alter table properties add column parking_entry_instructions varchar(5000);
alter table properties add column check_out_instructions varchar(5000);

create table returning_guest_verification_challenges (
    id uuid primary key,
    guest_link_id uuid not null references guest_links(id) on delete cascade,
    guest_id uuid not null references guests(id),
    code_hash varchar(100) not null,
    expires_at timestamp with time zone not null,
    sent_at timestamp with time zone not null,
    attempts integer not null default 0,
    verified_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index returning_guest_challenges_link_idx on returning_guest_verification_challenges(guest_link_id, created_at desc);
