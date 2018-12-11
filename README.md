JIShop (Backend)
================

## What is JIShop?

JIShop is a webapp written in scala with PlayFramework which goal is to provide a new ticketing site for Japan Impact.

## How to build?

To build the app, simply run `sbt dist` from the root directory of the project. The app will be contained in a single zip file, located
in `target/universal/jishop-version.zip`.

## How to run?

This file can be extracted anywhere. To start the app, just run the `bin/jishop` script.
As an example, the command used to start the process currently is

    nohup su --shell=/bin/bash --command="jishop-1.0/bin/jishop" play-jishop > application.log 2>&1 & echo $! > run.pid

Some details in the application configuration must be changed. The config file
is in `conf/application.conf`.

The http port can easily be set in the config

    http.port = 16060

For the database evolutions to apply automatically, it can be good practice to set

    play.evolutions.autoApply=true

You must however be sure that your evolutions are good before deploying the app in production.

Don't forget also to configure polybanking details, as well as recaptcha secrets, mailer details, ...

## Develop

To start a local development server, just run `sbt boot` in the root directory.

## Stuff to do:

- [ ] Write tests
- [ ] Write tests
- [ ] Write more tests
- [ ] Or at least test by hand
- [ ] Refactor/Reorder code
- [ ] More comments and documentation!

## About ticket generation templates

For tickets generation, we use Twirl templates. The base template is defined in `views/template.scala.html`. It defines 
the header (title, poster, top barcode) and the footer (warning text, generation time, bottom barcode). Then, there is a
template for order tickets (`orderTicket.scala.html`) and an other one for admission tickets (`ticket.scala.html`).

The path to the poster image is defined in the configuration (`polyjapan.posterFile`). As it is embedded in the PDF 
tickets, you have to make it light (max 500 KiB). The templates will work better if the image is 627px wide.