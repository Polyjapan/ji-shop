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

## Random stuff I thought about

For tickets generation, when looking for the template, we check in that order:

1. `templates/by-product/{product_id}.tpl`
1. `templates/by-category/{category_id}.tpl`
1. `templates/by-event/{event_id}.tpl`
1. `templates/general.tpl`

A template file looks like this (not really definitive but that's an idea):

    {
        base_image: "a_path_to_an_image",
        components: [
            {
                x: /* x position from left */,
                y: /* y position from top */,
                font_family: /* font family to use */,
                font_size: /* font size to use */,
                content: /* the actual text to write, using variables */   
            }
        ]
    }

The variables used are:

- `%{table_name}.{column_name}%` the value of a column in a table

### Alternative option 

Instead of using .tpl files we can use more SQL tables (yayyyyy).

- `templates`: `id`, `base_image`
- `templates_components`: `id`, `template_id`, `x`, `y`, `font_family`, `font_size`, `content`
- `templates_by_product`: `product_id`, `template_id`
- `templates_by_category`: `category_id`, `template_id`
- `templates_by_event`: `event_id`, `template_id`

Default template will always be template with id = 0