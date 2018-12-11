# --- !Ups

ALTER TABLE tickets ADD ticket_removed BOOLEAN DEFAULT false;
ALTER TABLE orders ADD order_removed BOOLEAN DEFAULT false;


# --- !Downs

ALTER TABLE tickets DROP ticket_removed;
ALTER TABLE orders DROP order_removed;