# --- !Ups

INSERT INTO ticket_templates (ticket_template_id, ticket_template_base_image, ticket_template_barcode_x, ticket_template_barcode_y, ticket_template_barcode_width, ticket_template_barcode_height)
 VALUES (0, 'not_existing.jpg', 0, 0, 0, 0);

UPDATE ticket_templates SET ticket_template_id = 0 WHERE ticket_template_base_image = 'not_existing.jpg'
  AND ticket_template_barcode_x = 0
  AND ticket_template_barcode_y = 0
  AND ticket_template_barcode_width = 0
  AND ticket_template_barcode_height = 0;

# --- !Downs

DELETE FROM ticket_templates WHERE ticket_template_id = 0;