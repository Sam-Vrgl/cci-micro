# FitConnect — Reservation et paiement de cours de sport

Plateforme de reservation de cours de sport en architecture microservices (Spring Boot 3.3 /
Spring Cloud 2023.0). Une reservation traverse trois services en cascade selon un **pattern Saga**
avec compensation, paiement differe (*Reserve Now, Pay Later*), politique d'annulation a 24h et
verrouillage optimiste contre les surreservations.

## Architecture

```
                        ┌──────────────────┐
                        │  eureka-server   │  :8761   annuaire des services
                        └────────▲─────────┘
                                 │ enregistrement / decouverte
   ┌─────────────────────────────┼──────────────────────────────────┐
   │                             │                                  │
┌──┴───────────┐        ┌────────┴────────┐                ┌────────┴────────┐
│ api-gateway  │        │  class-service  │                │ payment-service │
│    :8080     │───────▶│      :8091      │                │      :8093      │
│  4 routes    │        │  catalogue,     │                │  paiements,     │
└──┬───────────┘        │  places (@Version)               │  remboursements │
   │                    └────────▲────────┘                └────────▲────────┘
   │                             │  Feign + Circuit Breaker          │
   │                    ┌────────┴──────────────────────────────────┴────────┐
   ├───────────────────▶│              booking-service  :8092               │
   │                    │   orchestrateur Saga + scheduler d'expiration     │
   │                    └────────────────────────┬──────────────────────────┘
   │                                             │
   │                                    ┌────────▼─────────────┐
   ├───────────────────────────────────▶│ notification-service │  :8094
   │                                    │  emails simules      │
   │                                    └──────────────────────┘
   ▼
config-server  :8888   configuration centralisee (config-repo/)
```

| Module | Port | Base H2 | Role |
|---|---|---|---|
| `eureka-server` | 8761 | — | Annuaire des services |
| `config-server` | 8888 | — | Configuration centralisee (profil `native`) |
| `api-gateway` | 8080 | — | Point d'entree unique, 4 routes |
| `class-service` | 8091 | `classdb` | Catalogue des cours, gestion des places |
| `booking-service` | 8092 | `bookingdb` | Reservations, orchestration Saga, scheduler |
| `payment-service` | 8093 | `paymentdb` | Paiements et remboursements simules |
| `notification-service` | 8094 | `notificationdb` | Notifications simulees et historique |

## Prerequis

- JDK 17 (le projet cible Java 17 ; il compile aussi sous un JDK plus recent)
- Maven 3.9 ou le wrapper fourni par IntelliJ IDEA
- Docker + Docker Compose (facultatif, pour la stack conteneurisee)

## Demarrage local

Depuis la racine, dans **cet ordre**, chaque commande dans son propre terminal :

```bash
mvn -pl config-server spring-boot:run     # doit demarrer en premier
mvn -pl eureka-server spring-boot:run
mvn -pl class-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Attendre le `Started ...Application` de chaque service avant de lancer le suivant.
`config-server` lit `config-server/config-repo/` **relativement a son repertoire de module** :
`mvn -pl config-server spring-boot:run` s'en charge, mais un `java -jar` doit etre lance depuis
`config-server/`.

Verifications :

- Eureka : <http://localhost:8761> — les 5 services doivent apparaitre enregistres
- Swagger : <http://localhost:8091/swagger-ui.html> (idem sur 8092, 8093, 8094)
- Console H2 : <http://localhost:8091/h2-console> (JDBC `jdbc:h2:mem:classdb`, user `sa`, sans mot de passe)
- Via la passerelle : `GET http://localhost:8080/api/classes`

`class-service` charge un jeu de cours de demonstration (`data.sql`) a chaque demarrage, dont un
cours presque complet et un cours dans moins de 24h pour eprouver les cas d'erreur.

## Docker Compose

```bash
docker compose up --build      # construit et demarre les 7 services
docker compose down
```

Les healthchecks garantissent que `config-server` et `eureka-server` sont prets avant le demarrage
des autres conteneurs.

## Tests

```bash
mvn test                       # 93 tests sur les 4 services metier
mvn -pl booking-service test   # un seul module
```

Repartition : `class-service` 24, `booking-service` 39, `payment-service` 17,
`notification-service` 13. Les tests unitaires simulent les collaborateurs avec Mockito ; les tests
d'integration montent le contexte Spring complet avec `MockMvc`, les clients Feign en `@MockBean`
et une base H2 dediee (`spring.cloud.config.enabled=false`, Eureka desactive).

Les deux scenarios imposes par le sujet sont couverts par
[`BookingControllerIntegrationTest`](booking-service/src/test/java/com/formation/booking/controller/BookingControllerIntegrationTest.java) :
`shouldCompleteFullBookingFlow` et `shouldCancelExpiredBookings`.

## Collection Postman

[`postman/FitConnect.postman_collection.json`](postman/FitConnect.postman_collection.json) —
30 requetes en 5 dossiers, jouables d'une traite avec le Runner :

1. **Gestion des cours** — creation, liste paginee, filtres, recherche, detail
2. **Reservation** — reservation de 2 places, statut `PENDING_PAYMENT`, places deja prises
3. **Paiement** — confirmation, statut `CONFIRMED`, paiement `SUCCESS`, notifications emises
4. **Annulation** — annulation, remboursement `REFUNDED`, places liberees
5. **Scenarios d'erreur** — surreservation (409), paiement refuse (402), transition invalide (409),
   annulation hors delais (409), cours inexistant (400), validation des champs (400)

Les identifiants (`classId`, `bookingId`, `paymentId`…) sont chaines automatiquement par les scripts
de test, et les dates de cours sont recalculees a chaque execution : la collection reste jouable
indefiniment sans retouche.

## Workflow de reservation (pattern Saga)

### Cas 1 — `POST /api/bookings` : reservation

1. `GET /api/classes/{classId}` — le cours existe, est `SCHEDULED`, et il reste assez de places.
   Instantane capture : `className`, `instructor`, `classDate`, `price`.
2. `PATCH /api/classes/{id}/increment?spots=n` — prise effective des places. Un `409` du
   class-service devient un `409` cote client (« Plus de places disponibles »).
3. Persistance en `PENDING_PAYMENT`, `paymentDeadline = maintenant + 1h`,
   `cancellationDeadline = classDate - 24h`, reference `BK-XXXXX`.
   **Compensation** : si la persistance echoue apres l'etape 2, les places sont rendues.
4. Notification `BOOKING_CONFIRMATION` (sans effet sur l'issue de la saga).
5. `201 Created`.

### Cas 2 — Plus de places

L'echec survient a l'etape 1 ou 2, avant toute ecriture locale : aucune compensation n'est
necessaire, la reponse est un `409 Conflict`.

### Cas 3 — `PATCH /api/bookings/{id}/confirm` : paiement

1. Verifications : statut `PENDING_PAYMENT` et `paymentDeadline` non depasse, sinon `409`.
2. `POST /api/payments` — le prestataire simule accepte en dessous de 100 EUR.
3. `SUCCESS` : la reservation passe `CONFIRMED`, notification `PAYMENT_CONFIRMATION`, `200 OK`.
   `FAILED` : `402 Payment Required`, la reservation **reste payable** (voir choix de conception).

### Cas 4 — `PATCH /api/bookings/{id}/cancel` : annulation

1. Verifications : reservation ni annulee ni terminee, `cancellationDeadline` non depasse, sinon `409`.
2. Si `CONFIRMED` : recuperation du paiement puis `POST /api/payments/{id}/refund`.
3. Liberation des places (`decrement`), passage en `CANCELLED`.
4. Notification `BOOKING_CANCELLED`, `200 OK`.

## Verrouillage optimiste

`FitnessClass` porte un champ `@Version`. Deux reservations simultanees ne peuvent pas ecrire
`currentParticipants` depuis la meme version : la seconde leve une
`ObjectOptimisticLockingFailureException`.

`FitnessClassService.updateParticipants` delimite chaque tentative par un `TransactionTemplate` et
**rejoue jusqu'a 3 fois** sur donnees fraiches. Ce choix est deliberat : deux reservations
concurrentes sur des places encore libres doivent **toutes deux aboutir**. Seule la saturation
reelle du cours produit un `409` (`NoSpotsAvailableException`), levee par l'entite elle-meme :

```java
public void incrementParticipants(int spots) {
    if (currentParticipants + spots > maxParticipants) {
        throw new NoSpotsAvailableException(id, availableSpots(), spots);
    }
    this.currentParticipants += spots;
}
```

## Clients Feign et Circuit Breaker

`booking-service` declare trois clients Feign enveloppes par Resilience4j
(`spring.cloud.openfeign.circuitbreaker.enabled: true`), chacun avec sa `FallbackFactory`.

Le circuit breaker route **toutes** les erreurs vers le repli, y compris les reponses metier :
chaque fabrique retraduit donc le statut HTTP en exception du domaine, et ne signale une
indisponibilite que pour les pannes reelles (connexion refusee, timeout, circuit ouvert).

| Client | Criticite | Repli |
|---|---|---|
| `ClassClient` | critique | `404` → 400, `409` → 409, panne → `503` : la saga s'arrete |
| `PaymentClient` | critique au paiement | `process` : toute erreur arrete la saga. `getByBookingId` / `refund` : `404`/`409` renvoient `null`, l'annulation se poursuit |
| `NotificationClient` | non critique | journalisation seule : une notification perdue n'annule jamais une reservation payee |

Parametrage dans [`config-repo/booking-service.yml`](config-server/config-repo/booking-service.yml) :
fenetre glissante de 10 appels, seuil d'echec 50 %, 10 s en etat ouvert, passage semi-ouvert
automatique.

**OkHttp est indispensable ici** (`feign-okhttp` + `spring.cloud.openfeign.okhttp.enabled: true`) :
le client HTTP par defaut de Feign, le `HttpURLConnection` du JDK, refuse la methode `PATCH` avec
un `Invalid HTTP method: PATCH`. Or `increment` et `decrement` sont des `PATCH`. Les tests
d'integration ne le revelent pas puisqu'ils simulent les clients Feign : le probleme n'apparait
qu'avec la stack reellement demarree.

## Scheduler

Implemente dans [`BookingScheduler`](booking-service/src/main/java/com/formation/booking/service/BookingScheduler.java),
crons externalises dans `config-repo` (`fitconnect.scheduler.*`) :

- **Expiration des paiements** (toutes les 5 minutes) — les reservations `PENDING_PAYMENT` dont
  `paymentDeadline` est passe sont annulees, leurs places liberees, une notification
  `BOOKING_CANCELLED` emise. Chaque reservation est traitee dans son propre `try/catch` : un service
  indisponible ne fait pas echouer le lot, la reservation sera reprise au passage suivant.
- **Rappel des cours** (toutes les heures) — les reservations `CONFIRMED` dont le cours commence
  dans moins de 24h recoivent une notification `BOOKING_REMINDER`. Un drapeau `reminderSent` evite
  les doublons.

`GET /api/bookings/expired` expose la meme liste, pour observer le travail du scheduler sans
attendre son declenchement.

## Endpoints

### class-service — `/api/classes`

| Methode | URL | Description |
|---|---|---|
| GET | `/api/classes` | Liste paginee et filtrable |
| GET | `/api/classes/search` | Recherche (memes filtres) |
| GET | `/api/classes/{id}` | Detail |
| POST | `/api/classes` | Creation |
| PUT | `/api/classes/{id}` | Mise a jour |
| DELETE | `/api/classes/{id}` | Annulation logique |
| PATCH | `/api/classes/{id}/increment?spots=n` | Prise de places (appele par booking-service) |
| PATCH | `/api/classes/{id}/decrement?spots=n` | Liberation de places |

Filtres : `category`, `level`, `location`, `instructor`, `status`, `dateFrom`, `dateTo`.
Pagination : `page`, `size`, `sort` (ex. `?page=0&size=10&sort=dateTime,asc`).

### booking-service — `/api/bookings`

| Methode | URL | Description |
|---|---|---|
| GET | `/api/bookings` | Liste |
| GET | `/api/bookings/{id}` | Detail |
| GET | `/api/bookings/user/{userId}` | Reservations d'un utilisateur |
| GET | `/api/bookings/expired` | Reservations impayees echues |
| POST | `/api/bookings` | Creation (saga) |
| PATCH | `/api/bookings/{id}/confirm` | Paiement et confirmation |
| PATCH | `/api/bookings/{id}/cancel` | Annulation |
| PATCH | `/api/bookings/{id}/complete` | Cloture |

### payment-service — `/api/payments`

| Methode | URL | Description |
|---|---|---|
| POST | `/api/payments` | Traitement d'un paiement |
| GET | `/api/payments/{id}` | Detail |
| GET | `/api/payments/booking/{bookingId}` | Paiement d'une reservation |
| GET | `/api/payments/user/{userId}` | Historique |
| POST | `/api/payments/{id}/refund` | Remboursement |

Simulation : `amount < 100` → `SUCCESS`, `amount >= 100` → `FAILED`.

### notification-service — `/api/notifications`

| Methode | URL | Description |
|---|---|---|
| POST | `/api/notifications` | Emission |
| GET | `/api/notifications/user/{userId}` | Historique |
| GET | `/api/notifications/pending` | En attente ou en echec |
| PATCH | `/api/notifications/{id}/retry` | Nouvelle tentative |

## Choix de conception

Le cahier des charges laisse plusieurs points ouverts ; voici les partis pris et leurs raisons.

**Le paquetage de `class-service` est `com.formation.fitclass`.** `class` est un mot-cle Java :
`com.formation.class` ne compile pas.

**L'annulation libere toujours les places, pas seulement pour une reservation confirmee.** Le sujet
imbrique le `decrement` sous « si status == CONFIRMED », mais les places sont prises des l'etape 2
de la creation, donc y compris en `PENDING_PAYMENT`. S'en tenir a la lettre du sujet laisserait des
places bloquees sur toute reservation annulee avant paiement.

**Un paiement refuse laisse la reservation en `PENDING_PAYMENT`** et repond `402 Payment Required`.
Le sujet autorise explicitement ce choix (« ou reste PENDING_PAYMENT pour retenter ») : il permet de
retenter avec un autre moyen de paiement avant l'echeance, et le scheduler annule de toute facon la
reservation a expiration. Un `402` distingue ce cas d'un conflit d'etat (`409`).

**Un paiement refuse est un `201` cote payment-service.** Le refus est un fait metier persiste,
porte par le champ `status`, pas une erreur de protocole : booking-service a besoin de la trace de
la tentative pour la restituer a l'utilisateur.

**`DELETE /api/classes/{id}` annule le cours au lieu de le supprimer** (`status = CANCELLED`). Des
reservations referencent le cours ; une suppression physique casserait leur historique.

**Le verrouillage optimiste est double d'un rejeu** plutot que de remonter directement en `409`
(voir la section dediee) : une collision de version n'est pas un manque de places.

**Le controle du delai d'annulation reste dans booking-service.** `payment-service` ne connait pas
la date du cours ; il execute le remboursement qu'on lui demande, la regle des 24h appartient a
l'orchestrateur.

**Les notifications sont emises hors du chemin critique.** Leur echec est journalise et absorbe a
deux niveaux (repli du client Feign, puis `NotificationPublisher`) : perdre un email ne doit jamais
annuler une reservation payee.

## Structure du projet

```
cci-micro/
├── pom.xml                     parent : Spring Boot 3.3.2, Spring Cloud 2023.0.3, Java 17
├── docker-compose.yml          7 services, reseau fitconnect-net, healthchecks
├── eureka-server/              :8761
├── config-server/
│   └── config-repo/            application.yml + 1 fichier par service
├── api-gateway/                :8080, 4 routes
├── class-service/              :8091  model · repository (+ specifications) · service · controller
├── booking-service/            :8092  + client/ (Feign & fallbacks) + BookingScheduler
├── payment-service/            :8093
├── notification-service/       :8094
├── postman/                    collection de bout en bout
d'implementation et suivi des livrables
```

Chaque service metier suit le meme decoupage : `model` (entites JPA et enums), `repository`,
`service` (regles metier et `*Mapper`), `controller` (REST), `dto`, `exception`
(`ApiError` + `GlobalExceptionHandler`), `config` (OpenAPI). Pas de Lombok : les accesseurs sont
explicites, comme dans le projet d'infrastructure reutilise.

## Codes de reponse

| Code | Signification |
|---|---|
| `400` | Validation en echec (`fieldErrors` detaille les champs) ou cours inexistant a la reservation |
| `402` | Paiement refuse par le prestataire ; la reservation reste payable |
| `404` | Ressource inconnue |
| `409` | Conflit metier : plus de places, transition de statut impossible, delai depasse |
| `503` | Service dependant injoignable ou circuit ouvert ; la saga s'est arretee sans effet de bord |
