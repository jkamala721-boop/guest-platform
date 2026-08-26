create table admin_users (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(100) not null,
    display_name varchar(120) not null,
    role varchar(30) not null,
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_login_at timestamp with time zone,
    constraint admin_users_email_key unique (email)
);

create table admin_sessions (
    id uuid primary key,
    admin_user_id uuid not null references admin_users(id) on delete cascade,
    token_hash varchar(64) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    constraint admin_sessions_token_hash_key unique (token_hash)
);

create index admin_sessions_admin_expires_idx on admin_sessions(admin_user_id, expires_at desc);
create index admin_sessions_active_expiry_idx on admin_sessions(revoked_at, expires_at);

create table admin_audit_log (
    id uuid primary key,
    admin_user_id uuid references admin_users(id),
    action varchar(80) not null,
    entity_type varchar(80),
    entity_id varchar(100),
    previous_state varchar(4000),
    new_state varchar(4000),
    reason varchar(1000),
    metadata_json varchar(4000),
    created_at timestamp with time zone not null
);

create index admin_audit_admin_created_idx on admin_audit_log(admin_user_id, created_at desc);
create index admin_audit_entity_created_idx on admin_audit_log(entity_type, entity_id, created_at desc);
create index admin_audit_action_created_idx on admin_audit_log(action, created_at desc);

