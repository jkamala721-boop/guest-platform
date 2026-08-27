create table admin_host_notes (
    id uuid primary key,
    host_id uuid not null references hosts(id),
    author_admin_id uuid not null references admin_users(id),
    note_type varchar(30) not null,
    content varchar(5000) not null,
    created_at timestamp with time zone not null
);

create index admin_host_notes_host_created_idx on admin_host_notes(host_id, created_at desc);
create index admin_host_notes_host_type_created_idx on admin_host_notes(host_id, note_type, created_at desc);
