alter table guests add column email_verified boolean not null default false;
alter table guests add column email_verification_code_hash varchar(100);
alter table guests add column email_verification_expires_at timestamp with time zone;
alter table guests add column email_verification_sent_at timestamp with time zone;
alter table guests add column email_verification_attempts integer not null default 0;
