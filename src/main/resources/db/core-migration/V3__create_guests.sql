create table guests (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    full_name varchar(160) not null,
    phone varchar(32) not null,
    email varchar(320) not null,
    id_type varchar(40),
    id_number varchar(100),
    nationality varchar(100),
    whatsapp_number varchar(32),
    notes varchar(2000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index guests_host_id_created_at_idx on guests(host_id, created_at desc);
