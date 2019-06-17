-- !Ups

create table refresh_tokens
(
    refresh_token_id int auto_increment,
    client_id int not null,
    refresh_token_created timestamp default NOW() not null,
    refresh_token_disabled BOOLEAN default false null,
    refresh_token_user_agent TEXT null,
    constraint refresh_tokens_pk
        primary key (refresh_token_id),
    constraint refresh_tokens_clients_client_id_fk
        foreign key (client_id) references clients (client_id)
            on delete cascade
);

create table refresh_tokens_logs
(
    refresh_token_id int not null,
    refresh_tokens_log_time TIMESTAMP default NOW() not null,
    refresh_tokens_log_user_agent TEXT null,
    refresh_tokens_log_ip VARCHAR(200) null,
    constraint refresh_tokens_logs_refresh_tokens_refresh_token_id_fk
        foreign key (refresh_token_id) references refresh_tokens (refresh_token_id)
);

-- !Downs

DROP TABLE refresh_tokens_logs;
DROP TABLE refresh_tokens;