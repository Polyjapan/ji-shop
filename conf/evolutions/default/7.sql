# --- !Ups

ALTER TABLE events ADD event_archived BOOLEAN DEFAULT false NOT NULL;

# --- !Downs

ALTER TABLE events DROP event_archived;