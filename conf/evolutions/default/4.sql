# --- !Ups

CREATE TABLE pos_payment_logs
(
  id INT PRIMARY KEY,
  order_id INT,
  pos_payment_method SET('CASH', 'CARD'),
  log_date TIMESTAMP DEFAULT NOW(),
  accepted BOOLEAN DEFAULT false,
  card_transaction_code VARCHAR(250) NULL,
  card_transaction_failure_cause VARCHAR(250) NULL,
  card_receipt_sent BOOLEAN DEFAULT false,
  card_transaction_message VARCHAR(250) NULL,

  CONSTRAINT pos_payment_logs_fk FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

# --- !Downs

DROP TABLE pos_payment_logs;
