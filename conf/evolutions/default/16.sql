-- !Ups

-- POS
-- Create column
alter table pos_configurations
    add event_id int null after pos_configuration_id;

alter table pos_configurations
    modify pos_configuration_accept_cards tinyint(1) default 1 not null after event_id;

-- Fill column
UPDATE pos_configurations pc
    INNER JOIN (
        SELECT DISTINCTROW pc.pos_configuration_id, e.event_id
        FROM pos_configurations pc
                 JOIN pos_items pi on pc.pos_configuration_id = pi.pos_configuration_id
                 JOIN products p on pi.product_id = p.product_id
                 JOIN events e on p.event_id = e.event_id
    ) T ON T.pos_configuration_id = pc.pos_configuration_id
SET pc.event_id = T.event_id
WHERE 1 = 1;

-- Make not null and create key
alter table pos_configurations modify event_id int not null;

alter table pos_configurations
    add constraint pos_configurations_events_event_id_fk
        foreign key (event_id) references events (event_id);



-- Scanning
-- Create column
alter table scanning_configurations
    add event_id int null after scanning_configuration_id;

alter table scanning_configurations
    modify accept_order_tickets tinyint(1) null after event_id;

-- Fill column
UPDATE scanning_configurations sc
    INNER JOIN (
        SELECT DISTINCTROW sc.scanning_configuration_id, e.event_id
        FROM scanning_configurations sc
                 JOIN scanning_items si on sc.scanning_configuration_id = si.scanning_configuration_id
                 JOIN products p on si.product_id = p.product_id
                 JOIN events e on p.event_id = e.event_id
    ) T ON T.scanning_configuration_id = sc.scanning_configuration_id
SET sc.event_id = T.event_id
WHERE 1 = 1;

-- Delete goodies only configs
DELETE
FROM scanning_configurations
WHERE scanning_configuration_id IN (
    SELECT sc.scanning_configuration_id
    FROM scanning_configurations sc
             JOIN (SELECT scanning_configuration_id, COUNT(scanning_items.product_id) AS cnt
                   FROM scanning_configurations
                            NATURAL LEFT JOIN scanning_items
                   GROUP BY scanning_configuration_id) t ON t.scanning_configuration_id = sc.scanning_configuration_id
    WHERE cnt = 0
);

-- Make not null and create key
alter table scanning_configurations modify event_id int not null;

alter table scanning_configurations
    add constraint scanning_configurations_events_event_id_fk
        foreign key (event_id) references events (event_id);



-- !Downs

-- Scanning
alter table scanning_configurations drop foreign key scanning_configurations_events_event_id_fk;

drop index scanning_configurations_events_event_id_fk on scanning_configurations;

alter table scanning_configurations drop column event_id;

-- POS
alter table pos_configurations drop foreign key pos_configurations_events_event_id_fk;

drop index pos_configurations_events_event_id_fk on pos_configurations;

alter table pos_configurations drop column event_id;

