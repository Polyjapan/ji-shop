# --- !Ups

CREATE TABLE scanning_configurations
(
  scanning_configuration_id int PRIMARY KEY AUTO_INCREMENT,
  scanning_configuration_name VARCHAR(250),
  accept_order_tickets TINYINT(1)
);

CREATE TABLE scanning_items
(
  scanning_configuration_id int,
  product_id int,
  CONSTRAINT scanning_items_pk PRIMARY KEY (product_id, scanning_configuration_id),
  CONSTRAINT scanning_items_item_fk FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT scanning_items_config_fk FOREIGN KEY (scanning_configuration_id) REFERENCES scanning_configurations (scanning_configuration_id)
);

# --- !Downs

DROP TABLE scanning_items;

DROP TABLE scanning_configurations;
