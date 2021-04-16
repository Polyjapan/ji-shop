-- !Ups

-- Remove scanning management, migrated to `scan` API

drop table scanning_items;
drop table scanning_configurations;


-- !Downs

create table scanning_configurations
(
    scanning_configuration_id int auto_increment
        primary key,
    event_id int not null,
    accept_order_tickets tinyint(1) null,
    scanning_configuration_name varchar(250) null,
    constraint scanning_configurations_events_event_id_fk
        foreign key (event_id) references events (event_id)
)
    charset=utf8mb4;

create table scanning_items
(
    scanning_configuration_id int not null,
    product_id int not null,
    primary key (product_id, scanning_configuration_id),
    constraint scanning_items_config_fk
        foreign key (scanning_configuration_id) references scanning_configurations (scanning_configuration_id),
    constraint scanning_items_item_fk
        foreign key (product_id) references products (product_id)
)
    charset=utf8mb4;

