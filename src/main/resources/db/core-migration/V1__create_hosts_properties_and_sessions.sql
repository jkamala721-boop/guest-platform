create table hosts (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(100) not null,
    full_name varchar(120) not null,
    phone varchar(32),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table properties (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    name varchar(160) not null,
    property_type varchar(40) not null,
    address varchar(500) not null,
    maps_url varchar(2048) not null,
    max_guests integer not null check (max_guests > 0),
    default_nightly_rate numeric(12, 2) not null check (default_nightly_rate >= 0),
    currency varchar(3) not null,
    check_in_time time not null,
    check_out_time time not null,
    wifi_name varchar(100),
    wifi_password varchar(200),
    house_rules varchar(5000),
    check_in_instructions varchar(5000),
    contact_phone varchar(32),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index properties_host_id_idx on properties(host_id);

create table host_sessions (
    id uuid primary key,
    host_id uuid not null references hosts(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index host_sessions_token_hash_idx on host_sessions(token_hash);
create index host_sessions_host_id_idx on host_sessions(host_id);
