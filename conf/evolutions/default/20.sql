# --- !Ups

ALTER TABLE pos_configurations ADD pos_configuration_accept_camipro BOOLEAN DEFAULT false NOT NULL;
alter table pos_payment_logs modify pos_payment_method set('CASH', 'CARD', 'CAMIPRO') null;

# --- !Downs

ALTER TABLE pos_configurations DROP pos_configuration_accept_camipro;
alter table pos_payment_logs modify pos_payment_method set('CASH', 'CARD') null;

