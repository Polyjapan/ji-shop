# --- !Ups

ALTER TABLE products
	ADD is_web_exclusive BOOLEAN DEFAULT false NOT NULL;

ALTER TABLE products
	ADD product_real_price INT DEFAULT -1;

# --- !Downs

ALTER TABLE products DROP is_web_exclusive;
ALTER TABLE products DROP product_real_price;