alter table hosts add column account_status varchar(20) not null default 'ACTIVE';
alter table hosts add column account_suspension_reason varchar(1000);

create table host_verifications (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    status varchar(30) not null,
    legal_name varchar(160) not null,
    verification_type varchar(40) not null,
    id_type varchar(30) not null,
    id_number_last4 varchar(4) not null,
    id_fingerprint varchar(64) not null,
    phone varchar(32) not null,
    country_code varchar(2) not null,
    submitted_at timestamp with time zone,
    review_started_at timestamp with time zone,
    reviewed_at timestamp with time zone,
    reviewed_by_admin_id uuid references admin_users(id),
    rejection_reason varchar(1000),
    suspension_reason varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint host_verifications_host_key unique (host_id)
);
create index host_verifications_status_submitted_idx on host_verifications(status, submitted_at desc);
create index host_verifications_reviewed_idx on host_verifications(reviewed_at desc);
create index host_verifications_fingerprint_idx on host_verifications(id_fingerprint);

create table host_verification_events (
    id uuid primary key,
    verification_id uuid not null references host_verifications(id),
    actor_type varchar(20) not null,
    actor_id uuid not null,
    event_type varchar(80) not null,
    previous_status varchar(30),
    new_status varchar(30) not null,
    reason varchar(1000),
    created_at timestamp with time zone not null
);
create index host_verification_events_verification_created_idx
    on host_verification_events(verification_id, created_at desc);

create table host_agreement_versions (
    id uuid primary key,
    version varchar(40) not null,
    title varchar(200) not null,
    content text not null,
    content_hash varchar(64) not null,
    effective_at timestamp with time zone not null,
    material_change boolean not null,
    active boolean not null,
    created_by_admin_id uuid references admin_users(id),
    created_at timestamp with time zone not null,
    constraint host_agreement_versions_version_key unique (version)
);
create index host_agreement_versions_active_effective_idx
    on host_agreement_versions(active, effective_at desc);

create table host_agreement_acceptances (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    agreement_version_id uuid not null references host_agreement_versions(id),
    event_type varchar(80) not null,
    accepted_at timestamp with time zone not null,
    ip_address_hash varchar(64),
    user_agent_summary varchar(250),
    created_at timestamp with time zone not null,
    constraint host_agreement_acceptances_host_version_key unique (host_id, agreement_version_id)
);
create index host_agreement_acceptances_host_accepted_idx
    on host_agreement_acceptances(host_id, accepted_at desc);
create index host_agreement_acceptances_version_idx
    on host_agreement_acceptances(agreement_version_id);
