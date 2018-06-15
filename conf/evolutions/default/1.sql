# --- !Ups
create table `clients` (`client_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`client_firstname` VARCHAR(100) NOT NULL,`client_lastname` VARCHAR(100) NOT NULL,`client_email` VARCHAR(180) NOT NULL UNIQUE,`client_email_confirm_key` VARCHAR(100) NULL,`client_password` VARCHAR(250) NOT NULL,`client_password_algo` VARCHAR(15) NOT NULL,`client_password_reset` VARCHAR(250), `client_password_reset_end` TIMESTAMP NULL);

create table `events` (`event_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`event_name` VARCHAR(250) NOT NULL,`event_location` VARCHAR(250) NOT NULL,`event_visible` BOOLEAN NOT NULL);

create table `orders` (`order_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`client_id` INTEGER not NULL,`order_tickets_price` DOUBLE NOT NULL,`order_total_price` DOUBLE NOT NULL,`order_payment_confirmed` TIMESTAMP NULL,`order_enter_date` timestamp DEFAULT now() NOT NULL, `order_source` SET('WEB', 'ONSITE', 'RESELLER', 'GIFT') DEFAULT 'WEB');

alter table `orders` add constraint `order_client_fk` foreign key(`client_id`) references `clients`(`client_id`) on update NO ACTION on delete NO ACTION;

create table `products` (`product_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`product_name` VARCHAR(250) NOT NULL,`product_price` DOUBLE NOT NULL,`product_description` TEXT NOT NULL,`product_long_description` TEXT NOT NULL, `event_id` INTEGER NOT NULL, `is_ticket` BOOLEAN NOT NULL, `product_max_items` INTEGER NOT NULL, `product_free_price` BOOLEAN DEFAULT FALSE);

alter table `products` add constraint `product_event_fk` foreign key(`event_id`) references `events`(`event_id`) on update NO ACTION on delete NO ACTION;

create table `ordered_products` (`ordered_product_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`product_id` INTEGER NOT NULL,`order_id` INTEGER NOT NULL,`ordered_product_paid_price` DOUBLE NOT NULL);

alter table `ordered_products` add constraint `ordered_product_order_fk` foreign key(`order_id`) references `orders`(`order_id`) on update NO ACTION on delete NO ACTION;

alter table `ordered_products` add constraint `ordered_product_product_fk` foreign key(`product_id`) references `products`(`product_id`) on update NO ACTION on delete NO ACTION;

create table `tickets` (`ticket_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY, `ticket_bar_code` VARCHAR(50) NOT NULL UNIQUE, `ticket_created_at` TIMESTAMP DEFAULT now() NOT NULL);

create table `claimed_tickets` (`ticket_id` INTEGER NOT NULL PRIMARY KEY, `ticket_claimed_at` TIMESTAMP DEFAULT now() NOT NULL, `ticket_claimed_by_admin` INTEGER NOT NULL);

alter table `claimed_tickets` add constraint `claimed_tickets_ticket_fk` foreign key(`ticket_id`) references `tickets`(`ticket_id`) on update NO ACTION on delete NO ACTION;

alter table `claimed_tickets` add constraint `claimed_tickets_client_fk` foreign key(`ticket_claimed_by_admin`) references `clients`(`client_id`) on update NO ACTION on delete NO ACTION;

create table `ordered_products_tickets` (`ordered_product_id` INTEGER NOT NULL UNIQUE, `ticket_id` INTEGER NOT NULL UNIQUE, PRIMARY KEY (ordered_product_id, ticket_id));

alter table `ordered_products_tickets` add constraint `ordered_products_tickets_product_fk` foreign key(`ordered_product_id`) references `ordered_products`(`ordered_product_id`) on update NO ACTION on delete NO ACTION;
alter table `ordered_products_tickets` add constraint `ordered_products_tickets_ticket_fk` foreign key(`ticket_id`) references `tickets`(`ticket_id`) on update NO ACTION on delete NO ACTION;

create table `orders_tickets` (`order_id` INTEGER NOT NULL UNIQUE, `ticket_id` INTEGER NOT NULL UNIQUE, PRIMARY KEY (order_id, ticket_id));


alter table `orders_tickets` add constraint `orders_tickets_order_fk` foreign key(`order_id`) references `orders`(`order_id`) on update NO ACTION on delete NO ACTION;
alter table `orders_tickets` add constraint `orders_tickets_ticket_fk` foreign key(`ticket_id`) references `tickets`(`ticket_id`) on update NO ACTION on delete NO ACTION;

create table `permissions` (`client_id` INTEGER NOT NULL,`permission` VARCHAR(180) NOT NULL);

alter table `permissions` add constraint `pk_permissions` primary key(`client_id`,`permission`);

alter table `permissions` add constraint `permissions_client_fk` foreign key(`client_id`) references `clients`(`client_id`) on update NO ACTION on delete CASCADE;

# --- !Downs

ALTER TABLE ordered_products
  DROP FOREIGN KEY ordered_product_product_fk;

DROP TABLE products;

DROP TABLE permissions;

ALTER TABLE ordered_products
  DROP FOREIGN KEY ordered_product_order_fk;

DROP TABLE ordered_products_tickets;

DROP TABLE orders_tickets;

DROP TABLE claimed_tickets;

DROP TABLE tickets;

DROP TABLE orders;

DROP TABLE ordered_products;

DROP TABLE events;

DROP TABLE clients;
