-- !Ups

alter table clients
    add client_cas_user_id int not null after client_id;

alter table clients
    drop column client_email_confirm_key;

alter table clients
    drop column client_password;

alter table clients
    drop column client_password_algo;

alter table clients
    drop column client_password_reset;

alter table clients
    drop column client_password_reset_end;

create unique index clients_client_cas_user_id_uindex
    on clients (client_cas_user_id);

-- !Downs

alter table clients
    drop column client_cas_user_id;

alter table clients
    add client_email_confirm_key VARCHAR(100) NULL,
    add client_password VARCHAR(250) not null,
    add client_password_algo VARCHAR(15) not null,
    add client_password_reset VARCHAR(250) not null,
    add client_password_reset_end TIMESTAMP null;
