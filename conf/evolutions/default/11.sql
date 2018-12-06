# --- !Ups

alter table orders modify order_source set('WEB', 'ONSITE', 'RESELLER', 'GIFT', 'PHYSICAL') default 'WEB' null;

# --- !Downs

update orders set ji_shop.orders.order_source = null where order_source = 'PHYSICAL';
alter table orders modify order_source set('WEB', 'ONSITE', 'RESELLER', 'GIFT') default 'WEB' null;

