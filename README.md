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
  -  [x] Can login using an old password that MUST be rehashed using a new algorithm then removed from the database
- [x] Actual shop
  -  [x] List products for the active edition 
  -  [x] Go to the payment page and pay
  -  [x] IPN script is triggered and the tickets are sent `/shop/ipn`
    -  [x] Each bought ticket get its own ticket and barcode (yes these two concepts share the same name)
    -  [x] If the order has a non-ticket item, an order ticket is generated too
  -  [x] Display bought tickets on the user page `/users/orders` and `/users/tickets`
    -  [x] Get an URL to generate again a given ticket
  -  [x] Avoid selling [too much] more than max allowed
- [ ] (Admin) Manage the shop
  -  [ ] Create and update events `POST /admin/events`
  -  [ ] Deep clone events `GET /admin/events/copy/:id`
  -  [ ] Create and update products within events `POST /admin/events/:id/products` and `PUT /admin/events/:id/products/:id`
  -  [ ] Create free tickets of any kind of item `(multiple endpoints to create orders)`
  -  [ ] Sell tickets with real money `(use JI10 code)`
- [ ] (Admin) Read data
  -  [ ] Export all tickets for a given edition to different lists
  -  [ ] Display stats 
  
## Other stuff to do:

- [ ] Write tests
- [ ] Write tests
- [ ] Write more tests
- [ ] Or at least test by hand
- [ ] Refactor/Reorder code
- [ ] More comments and documentation!

## Ticket generation templates

For tickets generation, we use Twirl templates. The base template is defined in `views/template.scala.html`. It defines 
the header (title, poster, top barcode) and the footer (warning text, generation time, bottom barcode). Then, there is a
template for order tickets (`orderTicket.scala.html`) and an other one for admission tickets (`ticket.scala.html`).

The path to the poster image is defined in the configuration (`polyjapan.posterFile`). As it is embedded in the PDF 
tickets, you have to make it light (max 500 KiB). The templates will work better if the image is 627px wide.