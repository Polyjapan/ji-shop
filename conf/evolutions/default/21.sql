-- !Ups

ALTER TABLE events ADD event_description TEXT NULL;

-- !Downs

ALTER TABLE events DROP event_description;