% Présentation de JI Shop
% Manuel à l'usage du comité et des développeurs
% Louis Vialar

-------

# Introduction

La boutique Japan Impact est accessible sur l'URL https://shop.japan-impact.ch/. Elle a été développée en 2018 pour remplacer le plugin WordPress "BasicTicketting" qui était jusqu'alors responsable de la gestion de la boutique. Les problèmes de ce plugin étaient les suivants :

 - Impossible à configurer sans modifier le code
 - Pas de scanning intégré, ce qui nécessitait de se reposer sur les scanners Offline de la FNAC
 - La modification des produits en vente entrainait la modification des logs des années passées

En outre, nous souhaitions aussi centraliser toutes les ventes (ventes sur place, ventes externes [FNAC], ventes en ligne, invitations) au même endroit, pour faciliter la comptabilité et la réutilisation des données l'année suivante. Le module conçu résout donc les problèmes indiqués plus haut, tout en apportant :

 - La possibilité, via une interface pratique et une app Android, de vendre des produits sur place tout en les stockant dans le système
 - La possibilité de modifier tous les paramètres via une interface en ligne complète et pratique
 - La possibilité de scanner des billets à l'entrée de la convention
 - Une vue de statistiques complète, encore à améliorer

# Tour du propriétaire

Cette partie fait un tour rapide des différents composants de l'application.

## Partie publique (Front Office)

La partie publique est ce qui est visible par les personnes non connectées ou connectées avec un compte "basique", sans permissions.

### Page d'accueil (homepage)

C'est la page sur laquelle sont listés les produits en vente, s'il y en a. S'il n'y en a pas, un message est affiché pour inviter le client à revenir plus tard.

### "Mes Commandes" (connecté uniquement)

Les personnes connectées peuvent consulter leurs commandes passées (pour cette édition comme les précédentes), afficher leur reçu, et re-télécharger leurs billets, sur cet espace.

### Connexion / Inscription

Permet aux visiteurs de se créer un compte. Une confirmation de l'email sera demandée pour tout nouveau compte, ou pour réinitialiser le mot de passe.

## Partie privée (Back Office)

Cette partie n'est accessible qu'aux comptes disposant de permissions suffisantes.

### Scan Mode (perm: `staff.scan_ticket`)

Permet aux staffs de scanner des billets. Avant de commencer à scanner, une page demande à l'utilisateur de choisir une **configuration**. La configuration définit quels billets sont acceptés ou refusés, et elle est définie par un administrateur (voir plus loin). 

Une fois la configuration choisie, un champ de texte est présenté. Le staff peut ensuite choisir de taper un code à la main, ou scanner en utilisant un scanner USB. Lorsque la touche "enter" est pressée, le code est vérifié auprès du serveur. Si le code est valide et accepté par la configuration actuelle, un message positif est affiché (indiquant le nom du produit), et le billet est marqué comme *scanné*. Un billet déjà *scanné* ne sera ensuite plus accepté.

Un billet peut être refusé si :

  - Il n'est pas accepté par cette configuration (mauvais billet : i.e. billet 1j au lieu de gold, ou billet goodies au lieu d'entrée)
  - Billet déjà *scanné* avant
  - Billet invalide : le code n'existe juste pas
  - Billet invalidé : le billet existait, mais a été supprimé par un administrateur (remboursement, annulation de commande...)  

> **Android** : cette fonctionalité est proposée sur l'app Android (onglet **SCAN** sur la page d'accueil après la connexion).

### Point de vente (perm: `staff.sell_on_site`)

Permet aux staffs de vendre des produits (billets, cup noodles, tshirts, ...). Avant de commencer à vendre, une page demande à l'utilisateur de choisir une **configuration**. Elle définit quels produits peuvent être vendus, et dans quel ordre ils sont affichés. Elle est définir par un administrateur (voir plus loin).

Une fois la configuration choisie, le staff se retrouve devant une grille de produits, définie par la configuration. Il peut ensuite cliquer (ou toucher) un produit pour l'ajouter au panier, qui est affiché **sur la droite de l'écran**. Toucher un produit dans le panier en retire un exemplaire (i.e. toucher "5 * billet 1 jour" le transforme en "4 * billet 1 jour", toucher "1 * tshirt" le fait disparaitre). En bas du panier, se trouve le bouton pour payer (ou les, si les cartes sont acceptées). Lorsqu'il est pressé, la vente est enregistrée (mais pas encore considérée comme terminée), et le paiement se fait. Dans le cas des paiements par cartes, le SumUp est déclanché. Pour un paiement cash, une interface d'aide au rendu de monnaie est affichée. Une fois la vente conclue, le résultat est stocké sur le serveur et affiché dans les statistiques, et un résumé des produits vendus s'affiche. Il n'est alors plus possible d'annuler la vente depuis cette interface.

> **Android** : cette fonctionalité est proposée sur l'app Android (onglet **POINT DE VENTE** sur la page d'accueil après la connexion).

### Admin (perms: `admin.?`)

Permet de modifier les différentes parties de la boutique. Vous pouvez depuis ce menu accéder à différentes options :

#### Utilisateurs

Liste les utilisateurs et permet de leur assigner de nouvelles permissions.
La permission `*` donne le droit de tout faire. `staff.*` donne toutes les permissions commençant par `staff.`, `admin.*` toutes celles commençant par `admin.`. `toto.tata` donne la permission `toto.tata`, et aucune autre (c'est un exemple hein). Il est nécessaire d'attendre quelques secondes pour que les permissions s'appliquent (jusqu'à 180 secondes).

#### Infos sur un billet

Comme le scan mode, mais en plus puissant : travaille sur toutes les éditions (donc peut scanner n'importe quoi), et affiche toutes les informations sur le billet, ainsi que l'historique (quand a-t-il été scanné, par exemple) et la commande dans laquelle il a été acheté.

#### Evénements

Permet de gérer les différents événements (voir les concepts essentiels un peu plus loin). Le bouton + permet d'en créer un nouveau. Au sein de chaque événement, on dispose des sous menus suivants :

##### Statistiques (page par défaut)

Cette page présente un résumé des ventes effectuées au sein de l'édition, par origine.

##### Modifier l'événement

Assez explicite, permet de modifier les paramètres de l'événement, et notamment de le cacher ou de l'archiver.

##### Produits en vente

Permet de gérer les différents produits. Pour la création et la modification, se référer à **Produits** dans la section **Concepts essentiels**.

Le bouton **Vider les produits non utilisés** permet de supprimer tous les produits qui n'ont jamais été vendus de l'événement. Il n'est pas possible de supprimer un événement qui contient des produits, et il n'est pas possible de supprimer un produit qui a été vendu. En conséquence, un événement est là pour toujours si il a réalisé des ventes !

Si vous modifiez le prix d'un produit, les comptes n'en seront pas falsifiés pour autant : lors de chaque vente, le prix effectivement payé est enregistré. Celui ci ne sera pas changé si le prix du produit est changé. En outre, modifier un produit venant d'un événement copié ne modifiera que la copie, et non pas l'original. Si vous augmentez le prix de "Billet 1 jour" dans "JI 12", cela ne changera pas le prix de "Billet 1 jour" dans "JI 11".

##### Listing de commandes

Affiche une liste brute de toutes les commandes passées sur la plateforme. Il est possible d'afficher les commandes supprimées et non payées en utilisant les cases à cocher. Une commande peut avoir plusieurs sources : 

 - `GIFT`: cadeau (en général, invitation)
 - `ONSITE`: vente sur place
 - `WEB`: achat en ligne

Cliquer sur un numéro de commande permet d'afficher plus de détails, et, via la page de détails, d'annuler une commande (ce qui demandera plusieurs confirmations).

##### Configurations de scan

Définit les configurations de scan (voir "scan"). Voir les concepts essentiels pour comprendre les composantes d'une configuration de scan.

##### Configurations caisse

Définit les configurations caisse (voir "point de vente"). Voir les concepts essentiels pour comprendre les composantes d'une configuration de caisse.

##### Exportation

Permet d'exporter la liste des billets sous différents formats : 

 - Export fnac: remplir le formulaire permet d'obtenir le CSV à passer à la FNAC pour mettre sur leurs scanners. Probablement pas utile, vu qu'on a nos propres scanners, mais sait on jamais.
 - Export de statistiques: permet d'exporter la liste des produits vendus, pour mettre dans les feuilles de calcul qui affichent des jolis graphes (en attendant que les jolis graphes arrivent sur le backoffice).

##### Import de billets

Permet d'importer des billets depuis une source externe. Seule la fnac est supportée pour le moment.
Il faut uploader le fichier contenant tous les codes barres. Seuls les codes barres pas encore importés seront ajoutés, donc il faut faire attention à ne pas se tromper. Une fois les codes chargés, une correspondance entre le produit fnac et le produit local sera demandée. Il suffit de remplir les différents champs et de valider l'importation. Les billets apparaîtront dès lors dans les statistiques du site, et seront scannables par la partie scan.

##### Cloner

Permet de... dupliquer un événement. C'est pratique, parce que nos produits changent peu d'année en année. Les ventes ne sont pas transférées, mais les produits et les configurations de scan et de vente le sont, permettant de ne pas refaire le même travail tous les ans.

# Concepts essentiels

## L'événement (ou édition)

Dans __JIShop__, le concept de base est l'événement. C'est lui qui contient tout le reste. Chaque "compte" séparé devrait normalement donner lieu à un événement séparé. Par exemple, `Japan Impact 11` est un événement, qui contiendra tout ce qui est nécessaire pour la 11ème édition de Japan Impact. `BAM 2019` peut contenir les produits vendus lors de la BAM.

Tout ce qui est contenu dans un événement ne peut être accédé que par cet événement. Si vous définissez un produit `p` dans un événement `A`, les billets `p` ne pourront pas être scannés par une configuration de l'événement `B`. Le produit `p` ne pourra pas non plus être mis en vente dans des configurations "Point de vente" (voir plus tard) de l'événement `B`.

Un événement est défini par un nom, ainsi que par quelques attributs élémentaires :

 - **Emplacement**: permet de changer l'adresse qui sera affichée sur les billets. Cela ne changera malheureusement pas le chemin à suivre pour y accéder, qui est indiqué sur le billet. La valeur de ce champ n'est utilisée que sur les billets, elle importe peu si l'événement ne vendra jamais de billets (i.e. BAM ou Projections)
 - **Visible au public**: si cette case n'est pas cochée, aucun des produits ne pourra apparaître sur la page d'accueil, et il sera donc impossible de les acheter pour les visiteurs (ou le comité).
 - **Archivé**: permet de cacher toutes les configurations de scan/vente des menus de vente et de scan. Il est intéressant d'archiver un événement passé et sur lequel on ne travaillera plus. 

Un événement contient ensuite différents objets, qui seront décrits dans les sections suivantes.

## Le produit

Sur JIShop, on est là pour vendre des trucs, en l'occurrence, des billets pour nos événements, mais pas seulement. L'unité de base pour décrire "un truc qu'on vend", c'est le **produit**.

Un produit est caractérisé par différents paramètres :

 - **Nom** : ce qui sera affiché sur le billet, sur l'accueil, lors d'un scan, sur un point de vente, ... Le nom du produit quoi _(Exemples : billet 2 jours ; cup noodle ; ...)_
 - **Prix** : le prix à payer pour obtenir ce produit (sera aussi utilisé pour le point de vente). Obligatoirement un nombre entier, mais il peut être négatif (permet d'implémenter une _réduction pour achat groupé_, par exemple)
 - **Prix libre** : permet de rendre le prix libre. Le prix est alors compris entre `Prix` et `Prix * 3` (le prix étant la valeur définie juste avant). Pratique pour les goodies qui sont supposés "crowdfunding" (même s'il est peu probable que les gens veuillent payer plus cher...)
 - **Prix réel estimé** : permet d'afficher la petite info "- xx %". Par ex, pour un billet 2 jours, on peut mettre ici 2 * le prix d'un billet 1 jour, pour bien montrer que le billet 2 jours est plus intéressant que 2 billets 1 jour.
 - **Image** pas utilisé pour le moment, mais dans le futur, ça permettra d'afficher des images à côté des items dans la boutique.
 - **Description courte** : utile uniquement pour les produits vendus directement en ligne, c'est le texte qui s'affiche sous le nom sur la page d'accueil
 - **Description longue** : idem, sauf que ce texte s'affiche uniquement sur le billet
 - **Stock restant** : permet d'implémenter des items en quantité limitée (i.e. billets gold). `-1` signifie illimité, `0` signifie épuisé.

Il existe également 3 "switches" (cases à cocher, qui peuvent être cochées ou pas) :

 - **Billet** : si cette case est cochée, le produit est considéré comme un billet. Sinon, le produit est un goodie.
 	- Un **billet** permet d'entrer dans l'événement. Il sera sélectionnable indépendament lors de la création d'une configuration de scan (alors qu'une configuration qui accepte des goodies les accepte forcément tous). En outre, tout billet acheté entraine la génération d'un... billet papier, avec un code barre unique
 	- Un **goodie** est... un goodie (non really ?). Lorsqu'un ou plusieurs goodies sont achetés, **un seul** billet est généré pour tous les goodies. Lorsque ce billet est scanné, la liste des goodies est affichée et ils peuvent être délivrés.
 - **Visible** : si coché, le produit est visible en homepage et peut être acheté. Si pas coché, bah... seul le comité peut le voir. Cela permet au comité de l'offrir en cadeau, mais jamais de l'acheter. Il apparaîtra grisé sur la page d'accueil pour être différencié facilement si invisible.
 - **Exclu web** : affiche une petite bulle "exclu web", pour informer le public que le produit ne sera pas vendu sur place.

## La configuration de scan

Une configuration de scan permet de définir une liste de produits qui seront considérés valides par un scanner. Typiquement, on aura une configuration "entrées samedi", un "entrées dimanche" et une "entrées gold", par exemple. La première acceptera les billets 2 jours et samedi, la seconde 2 jours et dimanche, et la dernière uniquement les gold. On ajoutera une configuration "goodies" qui n'accepte que les billets de goodies.

Le samedi, un billet "dimanche" ne sera pas accepté, puisque la configuration "entrées samedi" est utilisée. Vice versa pour le dimanche. Un billet 2 jours scanné via une configuration sera considéré comme scanné "tout court" : un billet 2 jours scanné le samedi ne peut pas être re-scanné le dimanche. _NB: ces dernières années, nous ne proposons plus de billets "samedi" ou "dimanche" mais des billets "1 jour". L'idée de séparer les deux reste pratique pour démontrer le concept._

Une édition peut avoir autant de configurations de scan que désiré.
Une configuration est définie par son **nom** et par le fait qu'elle accepte ou non les **goodies**. Si elle les accepte, elle pourra scanner tous les billets comtenant des goodies. Dans le cas contraire, elle ne pourra pas les scanner.

Au sein d'une configuration, on peut très facilement **accepter** des produits. Les goodies ne peuvent pas être acceptés individuellement (ils seront toujours ou jamais acceptés, selon la configuration). Seuls les produits de types "billet" peuvent donc être acceptés. 

## La configuration de caisse

Une configuration de caisse (ou de point de vente) permet de définir des items qui peuvent être vendus. On peut avoir autant de configurations de caisse que désiré. 

Quelques exemples de configurations de caisse : `Ventes sur place` (vend les billets 1j par catégories), `Upgrades préventes` (permet aux préventes tarif réduit n'ayant pas le bon justificatif de "se mettre à niveau") ou encore `Caisse projection` (contenant les produits vendus en projection).

Une configuration est définie par un **nom**, et elle dispose d'un switch permettant d'accepter ou de refuser les cartes bancaires. Si les cartes sont acceptées, l'option de paiement par carte sera affichée sur l'interface.

On peut ensuite ajouter des produits dans une configuration de caisse. Lors de l'ajout d'un produit, on va vouloir définir plusieurs paramètres : 

 - Colonne et ligne : commencent à 0, permettent de placer le produit sur la grille
 - Couleur de fond : définit la couleur de la case
 - Couleur de texte : définit la couleur du texte sur la case

Cela permettra ensuite d'afficher correctement la grille de produit sur l'interface web et sur l'application android.


# Tour du développeur

To come.
