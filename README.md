JIShop
======

## What is JIShop?

JIShop is a webapp written in scala with PlayFramework which goal is to provide a new ticketing site for Japan Impact.

## Why JIShop?

The current Japan Impact shop is a PHP plugin for wordpress which has become unmaintainable because of successive changes year
after year. The goal of this project is to create a new plateform that can be used correctly year after year.

## Components

- [ ] Register / login
  -  [ ] Requires to verify email
  -  [ ] Can login using an old password that MUST be rehashed using a new algorithm then removed from the database
- [ ] Pick tickets
- [ ] Fill contact information on each ticket, and picture when needed
- [ ] Pay for the tickets and get them by email and on your profile page
- [ ] (Admin) Change the type of tickets sold
- [ ] (Admin) Extract tickets in different formats (including the format required by FNAC)