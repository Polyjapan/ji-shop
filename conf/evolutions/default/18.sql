-- !Ups

CREATE TABLE images (
    image_id int auto_increment primary key,
    image_category varchar(200),
    image_name varchar(200),
    image_width int,
    image_height int,
    image_size_bytes int
);

-- !Downs

DROP TABLE images;