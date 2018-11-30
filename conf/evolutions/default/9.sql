# --- !Ups

ALTER TABLE products ADD product_image VARCHAR(255) default NULL null;


# --- !Downs

ALTER TABLE products DROP product_image;