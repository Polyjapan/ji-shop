# --- !Ups

CREATE TABLE intranet_tasks
(
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(150),
  priority INT,
  state SET('SENT', 'REFUSED', 'WAITING', 'INPROGRESS', 'DONE', 'DROPPED'),
  event INT,
  created_by INT,
  created_at TIMESTAMP DEFAULT now(),

  CONSTRAINT intranet_tasks_events_fk FOREIGN KEY (event) REFERENCES events (event_id),
  CONSTRAINT intranet_tasks_clients_fk FOREIGN KEY (created_by) REFERENCES clients (client_id)
);

CREATE TABLE intranet_tasks_logs
(
  id INT PRIMARY KEY AUTO_INCREMENT,
  task_id INT,
  target_state SET('SENT', 'REFUSED', 'WAITING', 'INPROGRESS', 'DONE', 'DROPPED'),
  created_by INT,
  created_at TIMESTAMP DEFAULT now(),

  CONSTRAINT intranet_tasks_logs_tasks_fk FOREIGN KEY (task_id) REFERENCES intranet_tasks (id),
  CONSTRAINT intranet_tasks_logs_clients_fk FOREIGN KEY (created_by) REFERENCES clients (client_id)
);

CREATE TABLE intranet_tasks_assignations_logs
(
  id INT PRIMARY KEY AUTO_INCREMENT,
  task_id INT,
  assignee INT,
  deleted BOOLEAN,
  created_by INT,
  created_at TIMESTAMP DEFAULT now(),

  CONSTRAINT intranet_tasks_assignations_logs_tasks_fk FOREIGN KEY (task_id) REFERENCES intranet_tasks (id),
  CONSTRAINT intranet_tasks_assignations_logs_clients_fk FOREIGN KEY (created_by) REFERENCES clients (client_id),
  CONSTRAINT intranet_tasks_assignations_logs_assignees_fk FOREIGN KEY (assignee) REFERENCES clients (client_id)
);

CREATE TABLE intranet_tasks_comments
(
  id INT PRIMARY KEY AUTO_INCREMENT,
  task_id INT,
  content TEXT,
  created_by INT,
  created_at TIMESTAMP DEFAULT now(),

  CONSTRAINT intranet_tasks_comments_tasks_fk FOREIGN KEY (task_id) REFERENCES intranet_tasks (id),
  CONSTRAINT intranet_tasks_comments_clients_fk FOREIGN KEY (created_by) REFERENCES clients (client_id)
);


CREATE TABLE intranet_tasks_tags
(
  task_id INT,
  tag VARCHAR(100),

  PRIMARY KEY (task_id, tag),

  CONSTRAINT intranet_tasks_tags_tasks_fk FOREIGN KEY (task_id) REFERENCES intranet_tasks (id)
);

CREATE TABLE intranet_tasks_assignations
(
  task_id INT,
  assignee INT,

  PRIMARY KEY (task_id, assignee),

  CONSTRAINT intranet_tasks_assignations_tasks_fk FOREIGN KEY (task_id) REFERENCES intranet_tasks (id),
  CONSTRAINT intranet_tasks_assignations_clients_fk FOREIGN KEY (assignee) REFERENCES clients (client_id)
);

# --- !Downs

DROP TABLE intranet_tasks_assignations;
DROP TABLE intranet_tasks_tags;
DROP TABLE intranet_tasks_comments;
DROP TABLE intranet_tasks_logs;
DROP TABLE intranet_tasks_assignations_logs;

DROP TABLE intranet_tasks;
