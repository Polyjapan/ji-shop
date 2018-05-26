# --- !Ups
create table `clients` (`client_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`client_firstname` VARCHAR(100) NOT NULL,`client_lastname` VARCHAR(100) NOT NULL,`client_email` VARCHAR(180) NOT NULL UNIQUE,`client_email_confirm_key` VARCHAR(100) NULL,`client_password` VARCHAR(250) NOT NULL,`client_password_algo` VARCHAR(15) NOT NULL,`client_password_reset` VARCHAR(250), `client_password_reset_end` TIMESTAMP NULL);

create table `events` (`event_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`event_name` VARCHAR(250) NOT NULL,`event_location` VARCHAR(250) NOT NULL,`event_visible` BOOLEAN NOT NULL);

create table `categories` (`category_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`event_id` INTEGER NOT NULL,`category_name` VARCHAR(250) NOT NULL,`category_is_ticket` BOOLEAN NOT NULL);

create table `orders` (`order_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`client_id` INTEGER NOT NULL,`order_tickets_price` DOUBLE NOT NULL,`order_total_price` DOUBLE NOT NULL,`order_payment_confirmed` TIMESTAMP NULL,`order_enter_date` timestamp DEFAULT now() NOT NULL);

create table `products` (`product_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`product_name` VARCHAR(250) NOT NULL,`product_price` DOUBLE NOT NULL,`product_description` TEXT NOT NULL,`product_long_description` TEXT NOT NULL,`category_id` INTEGER NOT NULL, `product_max_items` INTEGER NOT NULL, `product_free_price` INTEGER NOT NULL);

create table `ordered_products` (`ordered_product_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`product_id` INTEGER NOT NULL,`order_id` INTEGER NOT NULL,`ordered_product_paid_price` DOUBLE NOT NULL,`ordered_product_bar_code` VARCHAR(50) NOT NULL UNIQUE);

create table `ticket_templates` (`ticket_template_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`ticket_template_base_image` VARCHAR(250) NOT NULL,`ticket_template_name` VARCHAR(180) NOT NULL UNIQUE,`ticket_template_barcode_x` INTEGER NOT NULL,`ticket_template_barcode_y` INTEGER NOT NULL,`ticket_template_barcode_width` INTEGER NOT NULL,`ticket_template_barcode_height` INTEGER NOT NULL);

create table `ticket_template_components` (`ticket_template_component_id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,`ticket_template_id` INTEGER NOT NULL,`ticket_template_component_x` INTEGER NOT NULL,`ticket_template_component_y` INTEGER NOT NULL,`ticket_template_component_font` VARCHAR(250) NOT NULL,`ticket_template_component_font_size` INTEGER NOT NULL,`ticket_template_component_content` TEXT NOT NULL);

create table `ticket_templates_by_product` (`ticket_template_id` INTEGER NOT NULL,`product_id` INTEGER NOT NULL);

alter table `ticket_templates_by_product` add constraint `pk_ticket_templates_by_product` primary key(`ticket_template_id`,`product_id`);

create table `ticket_templates_by_category` (`ticket_template_id` INTEGER NOT NULL,`category_id` INTEGER NOT NULL);

alter table `ticket_templates_by_category` add constraint `pk_ticket_templates_by_category` primary key(`ticket_template_id`,`category_id`);

create table `ticket_templates_by_event` (`ticket_template_id` INTEGER NOT NULL,`event_id` INTEGER NOT NULL);

alter table `ticket_templates_by_event` add constraint `pk_ticket_templates_by_event` primary key(`ticket_template_id`,`event_id`);

create table `permissions` (`client_id` INTEGER NOT NULL,`permission` VARCHAR(180) NOT NULL);

alter table `permissions` add constraint `pk_permissions` primary key(`client_id`,`permission`);

alter table `categories` add constraint `category_event_fk` foreign key(`event_id`) references `events`(`event_id`) on update NO ACTION on delete NO ACTION;

alter table `orders` add constraint `order_client_fk` foreign key(`client_id`) references `clients`(`client_id`) on update NO ACTION on delete NO ACTION;

alter table `products` add constraint `product_category_fk` foreign key(`category_id`) references `categories`(`category_id`) on update NO ACTION on delete NO ACTION;

alter table `ordered_products` add constraint `ordered_product_order_fk` foreign key(`order_id`) references `orders`(`order_id`) on update NO ACTION on delete NO ACTION;

alter table `ordered_products` add constraint `ordered_product_product_fk` foreign key(`product_id`) references `products`(`product_id`) on update NO ACTION on delete NO ACTION;

alter table `ticket_template_components` add constraint `ticket_template_components_template_fk` foreign key(`ticket_template_id`) references `ticket_templates`(`ticket_template_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_product` add constraint `ticket_templates_by_product_product_fk` foreign key(`product_id`) references `products`(`product_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_product` add constraint `ticket_templates_by_product_template_fk` foreign key(`ticket_template_id`) references `ticket_templates`(`ticket_template_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_category` add constraint `ticket_templates_by_category_category_fk` foreign key(`category_id`) references `categories`(`category_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_category` add constraint `ticket_templates_by_category_template_fk` foreign key(`ticket_template_id`) references `ticket_templates`(`ticket_template_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_event` add constraint `ticket_templates_by_event_event_fk` foreign key(`event_id`) references `events`(`event_id`) on update CASCADE on delete CASCADE;

alter table `ticket_templates_by_event` add constraint `ticket_templates_by_event_template_fk` foreign key(`ticket_template_id`) references `ticket_templates`(`ticket_template_id`) on update CASCADE on delete CASCADE;

alter table `permissions` add constraint `permissions_client_fk` foreign key(`client_id`) references `clients`(`client_id`) on update NO ACTION on delete CASCADE;

# --- !Downs

DROP TABLE ticket_templates_by_product;

DROP TABLE ticket_templates_by_event;

DROP TABLE ticket_templates_by_category;

ALTER TABLE ticket_template_components
  DROP FOREIGN KEY ticket_template_components_template_fk;

DROP TABLE ticket_templates;

DROP TABLE ticket_template_components;

ALTER TABLE ordered_products
  DROP FOREIGN KEY ordered_product_product_fk;

DROP TABLE products;

DROP TABLE permissions;

ALTER TABLE ordered_products
  DROP FOREIGN KEY ordered_product_order_fk;

DROP TABLE orders;

DROP TABLE ordered_products;

ALTER TABLE categories
  DROP FOREIGN KEY category_event_fk;

DROP TABLE events;

DROP TABLE clients;

DROP TABLE categories;

