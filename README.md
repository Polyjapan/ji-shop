JIShop (Backend)
================

## What is JIShop?

JIShop is a webapp written in scala with PlayFramework which goal is to provide a new ticketing site for Japan Impact.

## Why JIShop?

The current Japan Impact shop is a PHP plugin for wordpress which has become unmaintainable because of successive changes year
after year. The goal of this project is to create a new plateform that can be used correctly year after year.

## Components (backend)

- [x] Register / login
  -  [x] Requires to verify email
  -  [ ] Can login using an old password that MUST be rehashed using a new algorithm then removed from the database
- [ ] Actual shop
  -  [x] List products for the active edition 
  -  [x] Go to the payment page and pay
  -  [ ] IPN script is triggered and the tickets are sent `/shop/ipn`
  -  [ ] Display bought tickets on the user page `/users/orders` and `/users/tickets`
    -  [ ] Each bought ticket get its own ticket and barcode (yes these two concepts share the same name)
    -  [ ] If the order has a non-ticket item, an order ticket is generated too
  -  [ ] Avoid selling [too much] more than max allowed
  -  [ ] Handle discount codes ? (optional)
- [ ] (Admin) Manage the shop
  -  [ ] Create and update events `POST /admin/events`
  -  [ ] Deep clone events `GET /admin/events/copy/:id`
  -  [ ] Create and update products within events `POST /admin/events/:id/products` and `PUT /admin/events/:id/products/:id`
  -  [ ] Create templates `POST /admin/templates`
  -  [ ] Update fonts and template bases `POST ...`
  -  [ ] Deep clone templates `GET /admin/templates/copy/:id`
  -  [ ] Assign templates to products/categories/events `POST ...`
  -  [ ] Update the default template `PUT /admin/templates/default`
  -  [ ] Create discount codes (optional) `POST /admin/discount`
  -  [ ] Preview templates `GET /admin/templates/[:id]`
  -  [ ] Create free tickets of any kind of item `(multiple endpoints to create orders)`
  -  [ ] Sell tickets with real money `(use JI10 code)`
- [ ] (Admin) Read data
  -  [ ] Export all tickets for a given edition to different lists
  -  [ ] Display stats 

## Ticket generation templates

For tickets generation, when looking for the template, we check in that order:

1. Template for this particular product ID (`ticket_templates_by_product`)
1. Template for this particular event ID (`ticket_templates_by_event`)
1. Default template (`ticket_template_id = 0`)

A template looks like this (actually implemented in the SQL fashion but that's easier to picture this way):

    {
        base_image: /* path to the font to use */,
        barcode: {
            x: /* x position of the barcode top left corner */,
            y: /* y position of the barcode top left corner */,
            width: /* width of the barcode */,
            height: /* height of the barcode */
            
            /* if height > width, the barcode will be made vertical */
        },
        components: [
            {
                x: /* x position from left */,
                y: /* y position from top */,
                font: /* path to the font to use */,
                font_size: /* font size to use */,
                content: /* the actual text to write, using variables */   
            }
        ]
    }

The variables usable in the content are:

- `%{table_name}.{column_name}%` the value of a column in a table

The SQL implementation uses `ticket_templates` to store the `base_image` field as well as an id and the barcode stuff, and the
`ticket_template_components` table to store the different components.