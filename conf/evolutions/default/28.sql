-- !Ups

CREATE TABLE frontpages(
    frontpage_id int auto_increment primary key not null,
    frontpage_title mediumtext unicode not null,
    frontpage_visibility set('Draft', 'Internal', 'Visible', 'Archived') default 'Draft' not null
);

CREATE TABLE frontpage_blocks
(
    block_id int auto_increment primary key not null,
    frontpage_id int not null,
    block_order int default 0 not null,
    block_visibility set('Draft', 'Internal', 'Visible', 'Archived') default 'Draft' not null,
    product_id int default null null,
    category_id int default null null,
    block_text mediumtext unicode default null null,
    block_image mediumtext unicode default null null,

    constraint frontpage_blocks_categories_category_id_fk
        foreign key (category_id) references categories (category_id),
    constraint frontpage_blocks_frontpages_frontpage_id_fk
        foreign key (frontpage_id) references frontpages (frontpage_id)
            on update cascade on delete cascade,
    constraint frontpage_blocks_products_product_id_fk
        foreign key (product_id) references products (product_id)
            on update cascade on delete cascade
);

-- !Downs

DROP TABLE frontpage_blocks;
DROP TABLE frontpages;