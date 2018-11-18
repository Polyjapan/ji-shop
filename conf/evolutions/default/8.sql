# --- !Ups

ALTER TABLE pos_configurations ADD pos_configuration_accept_cards BOOLEAN DEFAULT true NOT NULL;

# --- !Downs

ALTER TABLE pos_configurations DROP pos_configuration_accept_cards;