-- !Ups

-- example query:
-- select concat(category_name, ', ') as category from products natural join products_categories natural join categories group by product_id

create table categories
(
    category_id   int primary key auto_increment not null,
    category_name mediumtext unicode             not null
);

create table products_categories
(
    product_id  int not null,
    category_id int not null,
    constraint products_categories_pk
        primary key (product_id, category_id),
    constraint products_categories_categories_category_id_fk
        foreign key (category_id) references categories (category_id)
            on update cascade on delete cascade,
    constraint products_categories_products_product_id_fk
        foreign key (product_id) references products (product_id)
            on update cascade on delete cascade
);
create index products_categories_category_id_index
    on products_categories (category_id);

create index products_categories_product_id_index
    on products_categories (product_id);

create table tax_rates
(
    tax_rate_id      int primary key auto_increment not null,
    tax_rate_name    varchar(100)                   not null,
    tax_rate         decimal(5, 2)                  not null,
    tax_rate_details mediumtext unicode             not null
);

insert into tax_rates(tax_rate_name, tax_rate, tax_rate_details)
VALUES ('Taux normal', 7.7, 'Biens, services par défaut, alcool'),
       ('Taux spécial', 3.7, 'Hôtels, petit déjeuner compris'),
       ('Taux réduit', 2.5, 'Alimentaire, livres, journaux, médicaments, autres biens d\'usage quotidien'),
       ('Non soumis à TVA', 0, 'Importation, ...');

create table clients_addresses
(
    client_address_id                 int primary key auto_increment not null,
    client_id                         int                            not null,
    client_address_street             MEDIUMTEXT UNICODE             not null,
    client_address_country            MEDIUMTEXT UNICODE             not null,
    client_address_city               MEDIUMTEXT UNICODE             not null,
    client_address_is_shipping        boolean default true           not null,
    client_address_is_billing         boolean default true           not null,
    client_address_send_mails         boolean default false          not null COMMENT 'If true, you can send physical promotional mail to this address',

    -- Allows an address to define an alternate person name from the one defined on the profile for delivery purposes
    client_address_override           boolean default false          not null COMMENT 'If false, ignore the other override fields',
    client_address_override_gender    mediumtext unicode             null COMMENT 'Sometimes ppl want to deliver stuff to other ppl homes',
    client_address_override_lastname  mediumtext unicode             null COMMENT 'Sometimes ppl want to deliver stuff to other ppl homes',
    client_address_override_firstname mediumtext unicode             null COMMENT 'Sometimes ppl want to deliver stuff to other ppl homes',
    client_address_override_company   mediumtext unicode             null COMMENT 'Sometimes ppl want to deliver stuff to other ppl homes',

    constraint clients_addresses_clients_client_id_fk
        foreign key (client_id) references clients (client_id)
            on update cascade on delete cascade
);

create index clients_addresses_client_id_index
    on clients_addresses (client_id);

ALTER TABLE clients
    ADD `client_company`            VARCHAR(60)        NULL AFTER client_lastname,
    ADD `client_gender`             VARCHAR(10)        NULL AFTER client_lastname,
    ADD `client_billing_email`      MEDIUMTEXT UNICODE NULL COMMENT 'If null, use client_email to send invoices' AFTER client_email,
    ADD `client_pricing`            DECIMAL(5, 2)      NULL DEFAULT 100 COMMENT 'Percetage of the price the client should actually pay.',
    ADD `client_vat_number`         VARCHAR(45)        NULL,
    ADD `client_remarks`            MEDIUMTEXT UNICODE NULL,
    ADD `client_creation_timestamp` TIMESTAMP          NULL DEFAULT CURRENT_TIMESTAMP,
    ADD `client_last_modification`  TIMESTAMP          NULL;



alter table products
    add product_tax_rate_id    int           null after product_price,
    add product_tax_rate_value decimal(5, 2) null COMMENT 'Ignored if tax_rate_id is set' after product_tax_rate_id,
    add product_vat_included   boolean       not null default true COMMENT 'If true, the VAT is already included in the price - if false, it\'s not' after product_tax_rate_value,

    add constraint products_tax_rates_fk
        foreign key (product_tax_rate_id) references tax_rates (tax_rate_id)
            on update cascade on delete restrict;

update products
set products.product_tax_rate_id  = 1,
    products.product_vat_included = true
where 1;

alter table orders
    drop order_tickets_price, -- useless, can be computed from the products list
    change order_total_price order_subtotal decimal(15, 2)     not null,
    add order_vat_included                  boolean            not null default true comment 'If true, the VAT is already included in subtotal (legacy)' after order_subtotal,
    add order_pricing                       decimal(5, 2)      not null default 100 comment 'Percentage of the sub-total to pay' after order_vat_included,
    add order_apply_vat                     boolean            not null default true comment 'If false, the VAT will not be applied to this order' after order_pricing,
    add order_remarks                       mediumtext unicode null,
    add order_total_taxes                   decimal(15, 2)     not null default (order_subtotal * (1 - (1 / 1.077))) comment 'The amount of taxes paid in the order',
    add order_total                         decimal(15, 2)     not null default order_subtotal comment 'The total amount paid in the order, taxes included'
;

-- default values here are for migration purposes only
-- TODO! breaking change.
alter table ordered_products
    modify product_id int null comment 'a null product ID indicates a manual rounding rebate',
    change ordered_product_paid_price ordered_product_unit_price decimal(10, 2) not null,
    add ordered_product_quantity                                 int            not null default 1,
    add ordered_product_subtotal_price                           decimal(10, 2) not null default ordered_product_unit_price,
    add gift                                                     boolean        not null default false,
    add vat_rate                                                 decimal(5, 2)  not null default 7.7,
    add vat_included                                             boolean        not null default true comment 'If true, the VAT is already included in unit/subtotal prices'
;

-- !Downs

drop index products_categories_category_id_index on products;
drop index products_categories_product_id_index on products;
drop table products_categories;
drop table categories;

alter table products
    drop foreign key products_tax_rates_fk;

alter table products
    drop product_tax_rate_id,
    drop product_tax_rate_value,
    drop product_vat_included;

drop table tax_rates;
drop index clients_addresses_client_id_index on clients_addresses;
drop table clients_addresses;

ALTER TABLE clients
    DROP `client_company`,
    DROP `client_gender`,
    DROP `client_billing_email`,
    DROP `client_pricing`,
    DROP `client_vat_number`,
    DROP `client_remarks`,
    DROP `client_creation_timestamp`,
    DROP `client_last_modification`;

alter table ordered_products
    change ordered_product_unit_price ordered_product_paid_price double not null,
    drop ordered_product_quantity,
    drop ordered_product_subtotal_price,
    drop gift,
    drop vat_rate,
    drop vat_included;

alter table orders
    add order_tickets_price                 double default 0, -- useless, can be computed from the products list
    change order_subtotal order_total_price double not null,
    drop order_vat_included,
    drop order_pricing,
    drop order_apply_vat,
    drop order_remarks,
    drop order_total_taxes,
    drop order_total
;