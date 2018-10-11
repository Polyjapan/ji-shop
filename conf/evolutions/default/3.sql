# --- !Ups

CREATE TABLE pos_configurations
(
  pos_configuration_id int PRIMARY KEY AUTO_INCREMENT,
  pos_configuration_name VARCHAR(250)
);

CREATE TABLE pos_items
(
  pos_configuration_id int,
  product_id int,
  row int,
  col int,
  color VARCHAR(50), -- the bootstrap class for background color
  font_color VARCHAR(50) -- the bootstrap class for font color

  -- position
  CONSTRAINT pos_items_pk PRIMARY KEY (product_id, pos_configuration_id),
  CONSTRAINT pos_items_item_fk FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT pos_items_config_fk FOREIGN KEY (pos_configuration_id) REFERENCES pos_configurations (pos_configuration_id)
);

# --- !Downs

DROP TABLE pos_items;

DROP TABLE pos_configurations;
