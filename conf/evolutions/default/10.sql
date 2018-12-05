# --- !Ups

CREATE TABLE order_logs
(
  order_log_id INT auto_increment,
  order_id INT null,
  order_log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  order_log_name VARCHAR(255) NOT NULL,
  order_log_details TEXT null,
  order_log_accepted BOOLEAN DEFAULT false,
  constraint order_logs_pk
    primary key (order_log_id),
  constraint order_logs_orders_order_id_fk
    foreign key (order_id) references orders (order_id)
);



# --- !Downs

DROP TABLE order_logs;