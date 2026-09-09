# Rendu — M2-DEV1 Microservices : FitConnect

Projet : plateforme de reservation et de paiement de cours de sport.
Depot : `cci-micro/` — 8 modules Maven, 99 classes Java, 93 tests automatises.

Documents lies : [README_CODE.md](README_CODE.md) (documentation technique complete) ·

---

## 1. Livrables attendus

| # | Livrable | Etat | Ou |
|---|---|---|---|
| 1 | Code source | OK | 4 services metier + 3 modules d'infrastructure |
| 2 | Fichiers de configuration dans `config-repo` | OK | [config-server/config-repo/](config-server/config-repo/) — 6 fichiers |
| 3 | Routes dans `api-gateway.yml` | OK | [api-gateway.yml](config-server/config-repo/api-gateway.yml) — 4 routes |
| 4 | Clients Feign avec Circuit Breaker | OK | [booking-service/client/](booking-service/src/main/java/com/formation/booking/client/) — 3 clients + 3 fallback factories |
| 5 | Pattern Saga complet | OK | [BookingService.java](booking-service/src/main/java/com/formation/booking/service/BookingService.java) |
| 6 | Verrouillage optimiste dans `class-service` | OK | [FitnessClass.java](class-service/src/main/java/com/formation/fitclass/model/FitnessClass.java) + [FitnessClassService.java](class-service/src/main/java/com/formation/fitclass/service/FitnessClassService.java) |
| 7 | Scheduler pour l'expiration des paiements | OK | [BookingScheduler.java](booking-service/src/main/java/com/formation/booking/service/BookingScheduler.java) |
| 8 | Collection Postman complete | OK | [postman/FitConnect.postman_collection.json](postman/FitConnect.postman_collection.json) — 30 requetes, 5 dossiers |
| 9 | Tests unitaires et d'integration | OK | 93 tests, `mvn test` vert |
| 10 | README detaille | OK | [README.md](README.md) |
| 11 | Docker Compose (bonus) | OK | [docker-compose.yml](docker-compose.yml) — 7 services |

---

## 2. Les 4 microservices

| Service | Port | Base H2 | Package |
|---|---|---|---|
| `class-service` | 8091 | `classdb` | `com.formation.fitclass` |
| `booking-service` | 8092 | `bookingdb` | `com.formation.booking` |
| `payment-service` | 8093 | `paymentdb` | `com.formation.payment` |
| `notification-service` | 8094 | `notificationdb` | `com.formation.notification` |

- Infrastructure reutilisee : `eureka-server` (8761), `config-server` (8888), `api-gateway` (8080).
- Tous les endpoints du cahier des charges sont implementes (8 pour les cours, 8 pour les
  reservations, 5 pour les paiements, 4 pour les notifications).
- Entites conformes au cahier des charges, avec toutes les contraintes demandees
  (nom >= 3 caracteres, duree dans {30,45,60,90}, capacite 5-30, prix >= 5.00, date future,
  1 a 4 places par reservation, etc.).
- Le paquetage de `class-service` est `fitclass` : `class` est un mot-cle Java, `com.formation.class`
  ne compile pas.

---

## 3. Les 5 specificites du TP

### 3.1 Orchestration complexe (3 services en cascade)

- `booking-service` est le seul orchestrateur : il appelle `class-service`, `payment-service` et
  `notification-service`. Les trois autres services ne s'appellent jamais entre eux.
- Les appels distants sont **hors transaction** : une transaction ouverte pendant un appel reseau
  bloquerait une connexion. La coherence vient de la compensation explicite, pas d'un rollback.

### 3.2 Paiement differe (Reserve Now, Pay Later)

- `POST /api/bookings` cree la reservation en `PENDING_PAYMENT` et **prend deja les places**.
- `paymentDeadline = bookingDate + 1h`. Le paiement se fait ensuite via
  `PATCH /api/bookings/{id}/confirm`.
- Simulation imposee : `amount < 100 EUR` -> `SUCCESS`, `amount >= 100 EUR` -> `FAILED`.

### 3.3 Politique d'annulation (24h)

- `cancellationDeadline = classDate - 24h`, calcule a la creation.
- Annulation dans les delais : remboursement si `CONFIRMED`, liberation des places, statut
  `CANCELLED`, notification.
- Annulation hors delais : `409 Conflict`, aucune place liberee, aucun remboursement.

### 3.4 Notifications

- 5 types couverts : `BOOKING_CONFIRMATION`, `PAYMENT_CONFIRMATION`, `BOOKING_REMINDER`,
  `BOOKING_CANCELLED`, `CLASS_CANCELLED`.
- Envoi simule (log) ; echoue volontairement sur une adresse absente ou invalide, ce qui rend
  testables le statut `FAILED` et `PATCH /api/notifications/{id}/retry`.
- **Hors chemin critique** : l'echec est absorbe a deux niveaux (fallback Feign puis
  `NotificationPublisher`). Perdre un email n'annule jamais une reservation payee.

### 3.5 Gestion des conflits (verrouillage optimiste)

- `FitnessClass` porte `@Version private Long version`.
- L'invariant est porte par l'entite elle-meme :

```java
public void incrementParticipants(int spots) {
    if (currentParticipants + spots > maxParticipants) {
        throw new NoSpotsAvailableException(id, availableSpots(), spots);
    }
    this.currentParticipants += spots;
}
```

- `FitnessClassService.updateParticipants` delimite chaque tentative par un `TransactionTemplate`
  et **rejoue jusqu'a 3 fois** sur `ObjectOptimisticLockingFailureException`.
- Justification : deux reservations concurrentes sur des places encore libres doivent **toutes deux
  aboutir**. Une collision de version n'est pas un manque de places ; seule la saturation reelle
  produit un `409`.
- **Preuve** : 12 reservations simultanees sur un cours de 5 places -> 5 x `201`, 7 x `409`,
  etat final exactement `5/5`. Aucune surreservation.

---

## 4. Workflow de reservation (pattern Saga)

### Cas 1 — Reservation reussie

1. `GET /api/classes/{classId}` : cours existant, `SCHEDULED`, places suffisantes. Snapshot capture
   (`className`, `instructor`, `classDate`, `price`).
2. `PATCH /api/classes/{id}/increment?spots=n` : prise effective des places.
3. Persistance en `PENDING_PAYMENT` + `paymentDeadline` + `cancellationDeadline` + reference `BK-XXXXX`.
   **Compensation** : si la persistance echoue apres l'etape 2, les places sont rendues (`decrement`).
4. Notification `BOOKING_CONFIRMATION`.
5. `201 Created`.

### Cas 2 — Plus de places

- L'echec survient a l'etape 1 ou 2, **avant toute ecriture locale**.
- Aucune compensation necessaire : rien n'a ete cree.
- Reponse `409 Conflict` — « Plus de places disponibles pour ce cours ».

### Cas 3 — Paiement reussi

1. Verifications : statut `PENDING_PAYMENT` et `paymentDeadline` non depasse, sinon `409`.
2. `POST /api/payments` avec `bookingId`, `bookingReference`, `userId`, `totalAmount`, `paymentMethod`.
3. `SUCCESS` -> `CONFIRMED` + notification `PAYMENT_CONFIRMATION` -> `200 OK`.
   `FAILED` -> `402 Payment Required`, la reservation reste payable.

### Cas 4 — Annulation dans les delais

1. Verifications : ni `CANCELLED` ni `COMPLETED`, `cancellationDeadline` non depasse, sinon `409`.
2. Si `CONFIRMED` : `GET /api/payments/booking/{id}` puis `POST /api/payments/{id}/refund`.
3. Liberation des places (`decrement`), statut `CANCELLED`.
4. Notification `BOOKING_CANCELLED` -> `200 OK`.

---

## 5. Clients Feign et Circuit Breaker

- 3 clients Feign, chacun avec une `FallbackFactory` : `spring.cloud.openfeign.circuitbreaker.enabled: true`
  + instances Resilience4j nommees comme les services.
- Point cle : **le circuit breaker route toutes les erreurs vers le fallback**, y compris les reponses
  metier. Chaque fabrique retraduit donc le statut HTTP en exception du domaine, et ne signale une
  indisponibilite que pour les pannes reelles.

| Client | Criticite | Politique de repli |
|---|---|---|
| `ClassClient` | critique | `404` -> 400, `409` -> 409, panne -> `503` ; la saga s'arrete |
| `PaymentClient` | critique au paiement | `process` : toute erreur arrete la saga. `getByBookingId`/`refund` : `404`/`409` -> `null`, l'annulation se poursuit |
| `NotificationClient` | non critique | journalisation seule ; la saga continue |

- Parametrage : fenetre glissante de 10 appels, seuil d'echec 50 %, 10 s ouvert, semi-ouvert automatique.
- **OkHttp est indispensable** (`feign-okhttp` + `spring.cloud.openfeign.okhttp.enabled: true`) : le
  client par defaut de Feign (`HttpURLConnection` du JDK) refuse la methode `PATCH`, or `increment`
  et `decrement` en sont. Bug invisible en test (clients Feign simules), decouvert en executant la
  stack reelle.

---

## 6. Scheduler

Crons externalises dans `config-repo` (`fitconnect.scheduler.*`).

- **Expiration des paiements** — toutes les 5 minutes :
  `PENDING_PAYMENT` + `paymentDeadline < now()` -> liberation des places -> `CANCELLED` ->
  notification `BOOKING_CANCELLED`.
  Chaque reservation est traitee dans son propre `try/catch` : un service indisponible n'interrompt
  pas le lot, la reservation est reprise au passage suivant.
- **Rappel des cours** — toutes les heures :
  `CONFIRMED` + cours dans moins de 24h -> notification `BOOKING_REMINDER`.
  Drapeau `reminderSent` pour eviter les doublons.
- `GET /api/bookings/expired` expose la meme liste, pour observer le scheduler sans attendre.

---

## 7. Tests (93 au total, `mvn test` vert)

| Module | Tests | Contenu |
|---|---|---|
| `class-service` | 24 | increment/decrement, invariant de capacite, specifications, CRUD, validations |
| `booking-service` | 39 | saga complete, compensation, deadlines, scheduler, fallbacks |
| `payment-service` | 17 | seuil des 100 EUR, remboursement, references uniques |
| `notification-service` | 13 | envoi, echec, retry, file d'attente |

Les tests nommes dans le cahier des charges sont tous presents :

- `shouldCreateBooking_whenSpotsAvailable` — 10 places, 5 prises, reservation de 2 -> `PENDING_PAYMENT`
- `shouldThrowException_whenNoSpotsAvailable` — 10 places, 9 prises, reservation de 2 -> exception
- `shouldCancelBookingAndRefund_whenWithinDeadline` — `CANCELLED` + rembourse + places rendues
- `shouldCompleteFullBookingFlow` — integration : creation -> paiement -> `CONFIRMED` -> places -> notifications
- `shouldCancelExpiredBookings` — integration : deadline passee -> scheduler -> `CANCELLED` -> places restaurees

Methode : Mockito pour les tests unitaires ; `@SpringBootTest` + `MockMvc` + clients Feign en
`@MockBean` + H2 dediee pour l'integration.

---

## 8. Collection Postman

30 requetes en 5 dossiers, jouables d'une traite avec le Runner (variables chainees par scripts,
dates de cours recalculees a chaque execution) :

1. **Gestion des cours** — creation, liste paginee, filtres, recherche, detail
2. **Reservation** — 2 places, `PENDING_PAYMENT`, places deja prises
3. **Paiement** — confirmation, `CONFIRMED`, paiement `SUCCESS`, notifications
4. **Annulation** — annulation, `REFUNDED`, places liberees
5. **Scenarios d'erreur** — surreservation, paiement refuse, transition invalide, annulation hors
   delais, cours inexistant, validation des champs

---

## 9. Verification sur stack reelle

Les 7 services ont ete demarres et l'API pilotee via la gateway (`:8080`).

| Scenario | Resultat observe |
|---|---|
| Reservation -> paiement -> confirmation | `PENDING_PAYMENT` -> `CONFIRMED`, `PAY-5I5VQ` `SUCCESS`, 2 notifications `SENT` |
| Annulation dans les delais | `CANCELLED`, paiement `REFUNDED`, places revenues a `0/10` |
| Surreservation | `409` — « Plus de places disponibles » |
| Paiement >= 100 EUR | `402`, reservation toujours `PENDING_PAYMENT` |
| Annulation < 24h | `409` — « Annulation non autorisee » |
| Cours inexistant / validation | `400` avec `fieldErrors` detailles |
| 12 reservations simultanees, 5 places | 5 x `201`, 7 x `409`, etat final `5/5` |

Egalement verifie : 5 services enregistres dans Eureka, 4 routes actives sur la gateway,
Swagger et console H2 accessibles sur les 4 services, `data.sql` charge (7 cours de demonstration).

---

## 10. Choix de conception et ecarts assumes

| Point | Decision | Raison |
|---|---|---|
| Liberation des places a l'annulation | **Toujours**, pas seulement si `CONFIRMED` | Les places sont prises des la creation, donc aussi en `PENDING_PAYMENT`. Suivre le sujet a la lettre laisserait des places fantomes sur toute reservation annulee avant paiement |
| Paiement refuse | Reservation **reste** `PENDING_PAYMENT`, reponse `402` | Explicitement autorise par le sujet. Permet de retenter avant l'echeance ; le scheduler annulera de toute facon. `402` distingue ce cas d'un conflit d'etat (`409`) |
| `POST /api/payments` sur refus | `201`, pas une erreur HTTP | Le refus est un fait metier persiste (`status = FAILED`) ; booking-service a besoin de la trace de la tentative |
| `DELETE /api/classes/{id}` | Annulation logique (`status = CANCELLED`) | Des reservations referencent le cours ; une suppression physique casserait leur historique |
| Verrouillage optimiste | Rejeu (3 tentatives) avant de renvoyer `409` | Une collision de version n'est pas un manque de places |
| Regle des 24h | Portee par `booking-service` | `payment-service` ne connait pas la date du cours ; il execute le remboursement, la regle appartient a l'orchestrateur |
| Paquetage `class-service` | `com.formation.fitclass` | `class` est un mot-cle Java |
| `GET /api/classes` et `/search` | Memes filtres, meme methode de service | Le sujet mentionne les filtres sur les deux routes ; pas de duplication |

---

## 11. Codes de reponse

| Code | Signification |
|---|---|
| `400` | Validation en echec (`fieldErrors`) ou cours inexistant a la reservation |
| `402` | Paiement refuse ; la reservation reste payable |
| `404` | Ressource inconnue |
| `409` | Conflit metier : plus de places, transition impossible, delai depasse |
| `503` | Service dependant injoignable ou circuit ouvert ; saga arretee sans effet de bord |

---

## 12. Lancement

```bash
# Local — dans cet ordre, un terminal par service
mvn -pl config-server spring-boot:run
mvn -pl eureka-server spring-boot:run
mvn -pl class-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl api-gateway spring-boot:run

# Docker
docker compose up --build

# Tests
mvn test
```

- Eureka : <http://localhost:8761> · Gateway : <http://localhost:8080>
- Swagger : <http://localhost:8091/swagger-ui.html> (idem 8092, 8093, 8094)
- `config-server` lit `config-repo/` relativement a son module : le lancer depuis `config-server/`
  en cas de `java -jar`.
