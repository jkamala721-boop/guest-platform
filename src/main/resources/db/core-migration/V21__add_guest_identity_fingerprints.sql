alter table guests add column identity_type varchar(40);
alter table guests add column identity_fingerprint varchar(64);
alter table guests add column masked_identity varchar(16);
create index guests_host_identity_fingerprint_idx on guests(host_id, identity_type, identity_fingerprint);
