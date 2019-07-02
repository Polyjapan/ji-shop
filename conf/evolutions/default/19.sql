-- !Ups

ALTER TABLE events ADD event_tickets_image VARCHAR(250) NULL;

-- !Downs

ALTER TABLE events DROP event_tickets_image;