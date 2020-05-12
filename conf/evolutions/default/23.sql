-- !Ups

drop table refresh_tokens_logs;
drop table refresh_tokens;

-- !Downs

create table refresh_tokens
(
    refresh_token_id int auto_increment
        primary key,
    client_id int not null,
    refresh_token_created timestamp default current_timestamp() not null,
    refresh_token_disabled tinyint(1) default 0 null,
    refresh_token_user_agent text null,
    constraint refresh_tokens_clients_client_id_fk
        foreign key (client_id) references clients (client_id)
            on delete cascade
);

create table refresh_tokens_logs
(
    refresh_token_id int not null,
    refresh_tokens_log_time timestamp default current_timestamp() not null,
    refresh_tokens_log_user_agent text null,
    refresh_tokens_log_ip varchar(200) null,
    constraint refresh_tokens_logs_refresh_tokens_refresh_token_id_fk
        foreign key (refresh_token_id) references refresh_tokens (refresh_token_id)
);

