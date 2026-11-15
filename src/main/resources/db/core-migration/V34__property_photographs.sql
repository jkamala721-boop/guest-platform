create table property_photos (
    id uuid primary key,
    property_id uuid not null references properties(id),
    object_prefix varchar(100) not null unique,
    state varchar(20) not null check (state in ('UPLOADING','READY','DELETE_PENDING')),
    sort_order integer not null check (sort_order >= 0),
    is_cover boolean not null default false,
    thumb_width integer, thumb_height integer, thumb_bytes integer,
    display_width integer, display_height integer, display_bytes integer,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    check (not is_cover or state = 'READY'),
    check (state <> 'READY' or (thumb_width > 0 and thumb_height > 0 and thumb_bytes > 0
        and display_width > 0 and display_height > 0 and display_bytes > 0
        and thumb_width is not null and thumb_height is not null and thumb_bytes is not null
        and display_width is not null and display_height is not null and display_bytes is not null))
);
create index property_photos_order_idx on property_photos(property_id, state, sort_order);
create index property_photos_cleanup_idx on property_photos(state, updated_at);
