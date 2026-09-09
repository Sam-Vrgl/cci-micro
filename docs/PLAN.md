# FitConnect — Plan d'implementation

Etat : **termine** — les 4 services sont implementes, `mvn test` est vert (93 tests).
Ce document liste ce qui reste a ecrire, fichier par fichier, et fige les choix de
conception sur les points ou l'enonce laisse une marge d'interpretation.

---

## 1. Ce qui est deja en place

| Element | Detail |
|---|---|
| `pom.xml` parent | `com.formation:fitconnect-parent`, Spring Boot 3.3.2, Spring Cloud 2023.0.3, Java 17, 7 modules |
| `eureka-server` | repris tel quel du projet precedent — port 8761 |
| `config-server` | repris — port 8888, profil `native`, lit `config-server/config-repo` |
| `api-gateway` | repris — port 8080, **4 routes reecrites** (classes / bookings / payments / notifications) |
| `config-repo/*.yml` | `application.yml` (commun) + 1 fichier par service metier (port, base H2, swagger). `booking-service.yml` porte en plus Feign + Resilience4j + les crons du scheduler |
| 4 modules metier | `pom.xml`, classe `*Application`, `OpenApiConfig`, `application.yml` (bootstrap) et `src/test/resources/application.yml` (config + eureka desactives, H2 `create-drop`) |
| `Dockerfile` x7 + `docker-compose.yml` | build multi-etapes Maven puis JRE, reseau `fitconnect-net`, healthchecks sur eureka et config |

Packages : `com.formation.fitclass` (class-service — `class` est un mot-cle Java, d'ou `fitclass`),
`com.formation.booking`, `com.formation.payment`, `com.formation.notification`.

Conventions reprises du projet existant : pas de Lombok (getters/setters explicites),
decoupage `model` / `repository` / `service` (+ `*Mapper`) / `controller` / `dto` / `exception`,
`ApiError` en `record`, `GlobalExceptionHandler` en `@RestControllerAdvice`, tests unitaires
Mockito et tests d'integration `@SpringBootTest` + `MockMvc` avec les clients Feign en `@MockBean`.

---

## 2. class-service (:8091) — catalogue et verrouillage optimiste

**Fichiers a creer** (`com.formation.fitclass`)

- `model/FitnessClass.java` — `@Entity`, `@Version private Long version`, methodes metier
  `incrementParticipants(int spots)` et `decrementParticipants(int spots)` portant l'invariant
  (leve `NoSpotsAvailableException` si `currentParticipants + spots > maxParticipants`).
- `model/Category.java` (YOGA, CROSSFIT, ZUMBA, PILATES, SPINNING, BOXING),
  `model/Level.java` (BEGINNER, INTERMEDIATE, ADVANCED),
  `model/ClassStatus.java` (SCHEDULED, CANCELLED, COMPLETED).
- `repository/FitnessClassRepository.java` — `JpaRepository` + `JpaSpecificationExecutor`.
- `repository/FitnessClassSpecifications.java` — predicats `category`, `level`, `location` et
  `instructor` (like insensible a la casse), `dateFrom` / `dateTo`, `status`.
- `dto/FitnessClassRequest.java` — validation : `name` `@NotBlank @Size(min = 3)` ;
  `description`, `instructor`, `gymLocation` `@NotBlank` ; enums `@NotNull` ;
  `durationMinutes` valide contre {30, 45, 60, 90} ; `maxParticipants` `@Min(5) @Max(30)` ;
  `price` `@DecimalMin("5.00")` ; `dateTime` `@NotNull @Future`.
- `dto/FitnessClassResponse.java`, `service/ClassMapper.java`.
- `service/FitnessClassService.java` — CRUD, `search(...)` paginee, `increment(id, spots)`, `decrement(id, spots)`.
- `controller/FitnessClassController.java` — les 8 endpoints de l'enonce.
- `exception/` — `ClassNotFoundException`, `NoSpotsAvailableException`, `InvalidClassStateException`,
  `ApiError`, `GlobalExceptionHandler` (404 / 409 / 400 et **`ObjectOptimisticLockingFailureException` en 409**).
- `resources/data.sql` — jeu de cours de demo, dates relatives, pour que la collection Postman soit
  jouable immediatement.

**Endpoints**

| Methode | URL | Retour |
|---|---|---|
| GET | `/api/classes` | `Page<FitnessClassResponse>` (`?page&size&sort`) |
| GET | `/api/classes/search` | idem + filtres `category, level, location, instructor, dateFrom, dateTo` |
| GET | `/api/classes/{id}` | 200 / 404 |
| POST | `/api/classes` | 201 + en-tete `Location` |
| PUT | `/api/classes/{id}` | 200 / 404 / 400 |
| DELETE | `/api/classes/{id}` | 204 |
| PATCH | `/api/classes/{id}/increment?spots=n` | 200 / **409** si plus de places |
| PATCH | `/api/classes/{id}/decrement?spots=n` | 200 |

**Choix de conception**

- `GET /api/classes` accepte les memes filtres que `/search` (l'enonce les mentionne sur les deux
  routes) ; les deux delèguent a la meme methode de service, pas de duplication.
- `DELETE` = **annulation logique** (`status = CANCELLED`), pas une suppression physique : des
  reservations referencent le cours. Documente dans le README.
- `spots` vaut 1 par defaut si le parametre est absent.
- Le verrouillage optimiste (`@Version`) est double d'un **retry court** (3 tentatives) sur
  `ObjectOptimisticLockingFailureException` dans `increment` / `decrement` : deux reservations
  simultanees sur des places encore libres doivent aboutir toutes les deux. C'est le depassement de
  `maxParticipants` qui doit renvoyer 409, pas la collision de version.

---

## 3. payment-service (:8093)

**Fichiers** (`com.formation.payment`) : `model/Payment.java`, `model/PaymentMethod.java`,
`model/PaymentStatus.java`, `repository/PaymentRepository.java` (`findByBookingId`,
`findByUserIdOrderByPaymentDateDesc`), `dto/PaymentRequest.java` et `PaymentResponse.java`,
`service/PaymentService.java` et `PaymentMapper.java`, `controller/PaymentController.java`,
`exception/` (`PaymentNotFoundException`, `PaymentNotRefundableException`, `ApiError`,
`GlobalExceptionHandler`).

| Methode | URL | Comportement |
|---|---|---|
| POST | `/api/payments` | simulation : `amount < 100` donne `SUCCESS` (+ `transactionId` genere), `amount >= 100` donne `FAILED`. **201 dans les deux cas** : un paiement refuse est un fait metier persiste, pas une erreur HTTP |
| GET | `/api/payments/booking/{bookingId}` | dernier paiement de la reservation, 404 sinon |
| POST | `/api/payments/{id}/refund` | `SUCCESS` devient `REFUNDED` ; sinon 409 |
| GET | `/api/payments/user/{userId}` | historique, tri decroissant |

`paymentReference` = `PAY-` + 5 caracteres alphanumeriques, unicite verifiee en base avec regeneration
en cas de collision. La regle « rembourser si annulation au moins 24h avant le cours » est portee par
**booking-service** (seul a connaitre `cancellationDeadline`) ; payment-service execute le remboursement.

---

## 4. notification-service (:8094)

**Fichiers** (`com.formation.notification`) : `model/Notification.java`, `model/NotificationType.java`,
`model/NotificationStatus.java`, `repository/NotificationRepository.java`, `dto/`,
`service/NotificationService.java` (+ envoi simule : log `INFO` puis `status = SENT`),
`controller/NotificationController.java`, `exception/`.

| Methode | URL | Comportement |
|---|---|---|
| POST | `/api/notifications` | persiste en `PENDING`, tente l'envoi, passe a `SENT` ou `FAILED` — 201 |
| GET | `/api/notifications/user/{userId}` | historique |
| GET | `/api/notifications/pending` | `status IN (PENDING, FAILED)` |
| PATCH | `/api/notifications/{id}/retry` | reessaie, met a jour `status` et `sentDate` |

L'envoi simule echoue si l'email est absent ou invalide : cela rend testables le statut `FAILED`
puis le `retry`.

---

## 5. booking-service (:8092) — orchestrateur Saga

**Clients Feign avec Circuit Breaker** (`client/`) : `ClassClient`, `PaymentClient`,
`NotificationClient`, chacun avec une `*FallbackFactory` (`@Component`) declaree via
`fallbackFactory = ...` sur `@FeignClient`. `spring.cloud.openfeign.circuitbreaker.enabled: true`
est deja dans `config-repo/booking-service.yml`, avec les instances Resilience4j nommees comme les services.

Politique de repli differenciee :

- `class-service` et `payment-service` sont **critiques** : le fallback releve une exception
  `*ServiceUnavailableException` (502/503) et la saga s'arrete proprement.
- `notification-service` est **non critique** : le fallback journalise et retourne `null`. Une
  notification perdue ne doit jamais annuler une reservation payee.

**Fichiers** : `model/Booking.java` (+ `BookingStatus`), `repository/BookingRepository.java`
(`findByUserId`, `findByStatusAndPaymentDeadlineBefore`, `findByStatusAndClassDateBetween`),
`dto/` (`BookingRequest`, `ConfirmPaymentRequest`, `BookingResponse`, `FitnessClassDto`,
`PaymentRequestDto`, `PaymentResponseDto`, `NotificationRequestDto`), `service/BookingService.java`,
`service/NotificationPublisher.java` (fabrique les libelles des 5 types de notification),
`service/BookingScheduler.java`, `controller/BookingController.java`, `exception/`.

### Cas 1 — POST /api/bookings

1. `ClassClient.getById(classId)` : 404 donne `ClassNotFoundForBookingException` (400) ;
   `status != SCHEDULED` donne 409 ; places insuffisantes donne 409.
2. `ClassClient.increment(classId, spots)` : un 409 remonte en `NoSpotsAvailableException` (409).
3. Persiste `Booking` : snapshot (`className`, `instructor`, `classDate`, `price`),
   `totalAmount = price x spots`, `status = PENDING_PAYMENT`, `paymentDeadline = now + 1h`,
   `cancellationDeadline = classDate - 24h`, `bookingReference = BK-XXXXX`.
   **Compensation** : si la persistance echoue apres l'increment, `decrement` puis propagation.
4. Notification `BOOKING_CONFIRMATION` (best effort, hors transaction).
5. `201 Created` + en-tete `Location`.

### Cas 3 — PATCH /api/bookings/{id}/confirm

1. `status == PENDING_PAYMENT` sinon 409 ; `paymentDeadline` non depasse sinon 409 (« paiement expire »).
2. `PaymentClient.process(...)` avec `bookingId`, `bookingReference`, `userId`, `totalAmount`,
   `paymentMethod`, `cardLastFour`.
3. `SUCCESS` donne `CONFIRMED` + notification `PAYMENT_CONFIRMATION`, reponse 200.
   `FAILED` : **la reservation reste `PENDING_PAYMENT`** — l'enonce autorise explicitement ce choix,
   il permet de retenter avant l'echeance et le scheduler annulera de toute facon a expiration.
   Reponse `402 Payment Required` avec la reference du paiement echoue.

### Cas 4 — PATCH /api/bookings/{id}/cancel

1. Refus si `status` vaut `CANCELLED` ou `COMPLETED` (409) ; refus si `cancellationDeadline`
   est depasse (409).
2. Si `CONFIRMED` : `GET /api/payments/booking/{id}` puis `POST /api/payments/{paymentId}/refund`.
3. **Liberation des places dans tous les cas** (`decrement`) : les places sont prises des la creation,
   y compris en `PENDING_PAYMENT`. L'enonce imbrique cette etape sous « si CONFIRMED », mais ne pas
   decrementer laisserait des places fantomes. Ecart assume et documente dans le README.
4. `status = CANCELLED`, notification `BOOKING_CANCELLED`, reponse 200.

### PATCH /api/bookings/{id}/complete

`CONFIRMED` devient `COMPLETED`, sinon 409. Base des futurs `NO_SHOW`.

### GET /api/bookings/expired

`status = PENDING_PAYMENT AND paymentDeadline < now` — expose pour le scheduler et pour Postman.

### Scheduler (`BookingScheduler`)

- `expirePendingPayments()` — cron `${fitconnect.scheduler.expiration-cron}` (5 minutes) : pour chaque
  reservation expiree, `decrement` des places, `status = CANCELLED`, notification `BOOKING_CANCELLED`.
  Chaque reservation est traitee dans son propre `try/catch` : un service indisponible ne doit pas
  interrompre le lot.
- `sendReminders()` — cron `${fitconnect.scheduler.reminder-cron}` (horaire) :
  `status = CONFIRMED AND classDate BETWEEN now+23h AND now+24h`, notification `BOOKING_REMINDER`.
  Un drapeau `reminderSent` sur `Booking` evite les doublons.

---

## 6. Tests

**Unitaires (Mockito)**

- `class-service` : `incrementParticipants` nominal et en depassement (`NoSpotsAvailableException`),
  mapping, specifications.
- `booking-service` : `shouldCreateBooking_whenSpotsAvailable`, `shouldThrowException_whenNoSpotsAvailable`,
  `shouldCancelBookingAndRefund_whenWithinDeadline`, plus confirm hors delai, confirm avec paiement
  refuse, annulation hors delai.
- `payment-service` : `< 100` donne SUCCESS, `>= 100` donne FAILED, refund d'un paiement non `SUCCESS` donne 409.
- `notification-service` : envoi donne SENT, email invalide donne FAILED, `retry` repasse a SENT.

**Integration (`@SpringBootTest` + `MockMvc`, clients Feign en `@MockBean`)**

- `shouldCompleteFullBookingFlow` : creation, confirm, `CONFIRMED`, places decrementees, notifications emises.
- `shouldCancelExpiredBookings` : reservation avec `paymentDeadline` passe, execution du scheduler,
  `CANCELLED` et places restituees.
- `class-service` : CRUD, 409 sur increment sature, recherche filtree paginee.

**Point d'attention** : le JDK installe sur ce poste est un **JDK 23**, le projet cible Java 17. La
compilation passe (`--release 17`), mais ByteBuddy/Mockito livres avec Spring Boot 3.3.2 ne connaissent
pas officiellement le bytecode 23. Si les tests echouent la-dessus : surcharger `<byte-buddy.version>`
dans le pom parent, ou installer un JDK 17.

---

## 7. Livrables

- [x] Code des 4 services (sections 2 a 5)
- [x] Fichiers de configuration dans `config-repo`
- [x] Routes dans `api-gateway.yml`
- [x] Clients Feign avec Circuit Breaker (3 clients + FallbackFactory differenciees)
- [x] Pattern Saga complet (creation, paiement, annulation, compensation)
- [x] Verrouillage optimiste dans `class-service` (@Version + rejeu)
- [x] Scheduler d'expiration des paiements + rappels a 24h
- [x] Collection Postman (`postman/`) — 30 requetes en 5 dossiers, variables chainees par scripts
- [x] Tests unitaires et d'integration (93 tests, `mvn test` vert)
- [x] README detaille : demarrage local, ordre de lancement, Docker, workflow Saga, choix de conception
- [x] Docker Compose (7 services)

---

## 8. Ordre d'execution propose

1. `class-service` — aucune dependance
2. `payment-service` et `notification-service` — independants
3. `booking-service` — consomme les trois
4. Tests, collection Postman, README
