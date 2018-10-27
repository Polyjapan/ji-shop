# --- !Ups

ALTER TABLE clients ADD client_accept_newsletter BOOLEAN DEFAULT FALSE NOT NULL;

# --- !Downs

ALTER TABLE clients DROP client_accept_newsletter;