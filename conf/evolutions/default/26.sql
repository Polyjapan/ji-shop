-- !Ups

drop table claimed_tickets;
drop table ordered_products_tickets;
drop table orders_tickets;
drop table tickets;

-- !Downs

create table tickets
(
    ticket_id int auto_increment
        primary key,
    ticket_bar_code varchar(50) not null,
    ticket_created_at timestamp default current_timestamp() not null,
    ticket_removed tinyint(1) default 0 null,
    constraint ticket_bar_code
        unique (ticket_bar_code)
)
    charset=utf8mb4;

create table claimed_tickets
(
    ticket_id int not null
        primary key,
    ticket_claimed_at timestamp default current_timestamp() not null,
    ticket_claimed_by_admin int not null,
    constraint claimed_tickets_client_fk
        foreign key (ticket_claimed_by_admin) references clients (client_id),
    constraint claimed_tickets_ticket_fk
        foreign key (ticket_id) references tickets (ticket_id)
)
    charset=utf8mb4;

create table ordered_products_tickets
(
    ordered_product_id int not null,
    ticket_id int not null,
    primary key (ordered_product_id, ticket_id),
    constraint ordered_product_id
        unique (ordered_product_id),
    constraint ticket_id
        unique (ticket_id),
    constraint ordered_products_tickets_product_fk
        foreign key (ordered_product_id) references ordered_products (ordered_product_id),
    constraint ordered_products_tickets_ticket_fk
        foreign key (ticket_id) references tickets (ticket_id)
)
    charset=utf8mb4;

create table orders_tickets
(
    order_id int not null,
    ticket_id int not null,
    primary key (order_id, ticket_id),
    constraint order_id
        unique (order_id),
    constraint ticket_id
        unique (ticket_id),
    constraint orders_tickets_order_fk
        foreign key (order_id) references orders (order_id),
    constraint orders_tickets_ticket_fk
        foreign key (ticket_id) references tickets (ticket_id)
)
    charset=utf8mb4;
