-- !Ups

insert into categories(category_id, category_name)
select event_id, event_name
from events;

insert into products_categories(product_id, category_id)
select product_id, event_id
from products;

insert into categories(category_id, category_name)
values (1000, 'Billets'),
       (2000, 'Goodies');

insert into products_categories(product_id, category_id)
select product_id, IF(is_ticket, 1000, 2000)
from products;

alter table products
    drop foreign key product_event_fk;

drop index product_event_fk on products;

alter table products
    drop event_id,
    drop is_ticket;

alter table pos_configurations
    drop foreign key pos_configurations_events_event_id_fk;

drop index pos_configurations_events_event_id_fk on pos_configurations;

alter table pos_configurations
    drop event_id;

drop table events;

-- !Downs

