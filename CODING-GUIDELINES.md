# Conventions & bonnes pratiques de développement — Projet Java / Spring

> Référence unique de l'équipe. Sert à écrire le code, relire les PR, et guider un agent IA.
> **Chaque règle a un exemple ✅ à faire / ❌ à ne pas faire.** Si une PR ne respecte pas ces règles, elle n'est pas mergée.

---

## 0. TL;DR (à lire avant chaque PR)

- Des **noms clairs** : le code se lit comme une phrase.
- Des **méthodes courtes** qui font une seule chose.
- On **retourne tôt** (early return) plutôt que d'imbriquer des `if`.
- Pas de **valeur magique** (nombre ou texte en dur) → constante ou enum.
- On **ne copie-colle pas** : si c'est écrit deux fois, on factorise.
- On **gère le null** (Optional, jamais de collection null).
- **Immutable par défaut** : `final` partout où c'est possible.
- Pas de logique métier dans le Controller, pas d'entité JPA exposée.
- Tout code métier a un **test**.
- PR ≤ 400 lignes, relue par 1 personne, pas de push direct sur `main`.

---

## 1. Nommer les choses

Un bon nom évite un commentaire. On écrit des noms complets, en anglais, pas d'abréviation.

❌ **À ne pas faire :**
```java
int d;                 // c'est quoi d ?
List<User> l;
boolean flag;
void proc(User u) {}
```

✅ **À faire :**
```java
int daysSinceLastLogin;
List<User> activeUsers;
boolean isEligibleForDiscount;
void sendWelcomeEmail(User user) {}
```

Règles :
- Classe = nom (`OrderService`), méthode = verbe (`calculateTotal`), booléen = question (`isValid`, `hasAccess`).
- Pas d'abréviation obscure (`usrMgr` → `userManager`).
- Un nom = un concept. Si tu utilises `list`, `data`, `info`, `temp`, `manager` c'est souvent qu'il manque un vrai nom.

---

## 2. Écrire des méthodes lisibles

### Courtes et une seule chose

Si une méthode dépasse ~20 lignes ou fait plusieurs choses, on la découpe.

❌
```java
void process(Order order) {
    // 60 lignes qui valident, calculent, sauvegardent, envoient un mail...
}
```

✅
```java
void process(Order order) {
    validate(order);
    applyDiscount(order);
    save(order);
    notifyCustomer(order);
}
```

### Retour anticipé (early return) plutôt que l'imbrication

❌ **À ne pas faire** (pyramide de `if`) :
```java
public String status(User user) {
    if (user != null) {
        if (user.isActive()) {
            if (user.hasSubscription()) {
                return "OK";
            } else { return "NO_SUB"; }
        } else { return "INACTIVE"; }
    } else { return "UNKNOWN"; }
}
```

✅ **À faire** (guard clauses) :
```java
public String status(User user) {
    if (user == null)              return "UNKNOWN";
    if (!user.isActive())          return "INACTIVE";
    if (!user.hasSubscription())   return "NO_SUB";
    return "OK";
}
```

### Peu de paramètres

Au-delà de 3-4 paramètres, on regroupe dans un objet.

❌ `void createUser(String first, String last, String email, String phone, String city, String zip)`
✅ `void createUser(CreateUserRequest request)`

---

## 3. Pas de valeurs magiques

Un nombre ou un texte en dur dans le code n'a pas de sens pour celui qui lit. → constante ou enum.

❌
```java
if (user.getAge() >= 18) { ... }
if (order.getStatus().equals("PAID")) { ... }
double total = price * 1.2;
```

✅
```java
private static final int LEGAL_AGE = 18;
private static final double VAT_RATE = 1.2;

if (user.getAge() >= LEGAL_AGE) { ... }
if (order.getStatus() == OrderStatus.PAID) { ... }   // enum, pas de String
double total = price * VAT_RATE;
```

Règle : **statuts, types, catégories → enum**, jamais des `String` comparées avec `.equals("...")`.

---

## 4. Ne pas se répéter (DRY)

Si le même bloc apparaît deux fois, on le factorise dans une méthode. Un bug corrigé à un endroit ne doit pas rester dans les copies.

❌ Le même calcul de remise copié dans 3 services.
✅ Une méthode `discountFor(Order order)` appelée partout.

⚠️ Nuance : ne pas sur-factoriser deux bouts de code qui se ressemblent par hasard mais évoluent séparément. On factorise ce qui est **le même concept**.

---

## 5. Gérer le null proprement

Le `NullPointerException` est le bug n°1. On l'évite par construction.

- Une méthode qui peut ne rien renvoyer retourne `Optional<T>`, pas `null`.
- Une méthode qui renvoie une liste retourne **toujours** une liste (vide si besoin), **jamais** `null`.

❌
```java
public User findUser(Long id) {
    return null; // l'appelant va oublier de tester et exploser
}
public List<Order> getOrders() {
    return null;
}
```

✅
```java
public Optional<User> findUser(Long id) {
    return repository.findById(id);
}
public List<Order> getOrders() {
    return orders == null ? List.of() : orders;
}
```

Côté appelant :
```java
User user = findUser(id).orElseThrow(() -> new UserNotFoundException(id));
```

---

## 6. Immutabilité & `final`

Moins une variable change, moins il y a de bugs. On met `final` par défaut.

- Champs de dépendances : `private final`.
- Variables locales qui ne changent pas : `final`.
- Préférer créer un nouvel objet plutôt que muter un existant quand c'est simple.

✅
```java
public class OrderService {
    private final OrderRepository repository;   // ne changera jamais
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

---

## 7. Commentaires & code mort

- Le commentaire explique **pourquoi**, pas **quoi** (le "quoi" doit être lisible dans le code).
- **On ne laisse pas de code commenté** : Git garde l'historique, on supprime.
- Pas de `TODO` orphelin sans ticket associé.

❌
```java
// incrémente i
i++;
// double oldTotal = compute(); // ancienne version, on garde au cas où
```

✅
```java
// On applique la TVA avant la remise car la réglementation FR l'impose
double total = applyVat(subtotal);
```

---

## 8. Logging (pas de `System.out.println`)

- Toujours un logger (SLF4J), jamais `System.out.println`.
- Bon niveau : `error` (une erreur réelle), `warn` (anomalie récupérable), `info` (événement métier), `debug` (détail technique).
- **Jamais** de donnée sensible dans les logs (mot de passe, token, données personnelles).

❌
```java
System.out.println("user " + user.getEmail() + " password " + user.getPassword());
```

✅
```java
private static final Logger log = LoggerFactory.getLogger(OrderService.class);
log.info("Order {} created for user {}", order.getId(), user.getId());
```

---

## 9. Gestion des erreurs

- Gestion **centralisée** avec `@RestControllerAdvice`, pas un `try/catch` bricolé dans chaque Controller.
- Exceptions métier **explicites** (`OrderNotFoundException`), pas de `RuntimeException` générique.
- **Jamais** de `catch` vide qui avale l'erreur.

❌
```java
try {
    doSomething();
} catch (Exception e) {
    // rien -> le bug disparaît sans laisser de trace
}
```

✅
```java
try {
    doSomething();
} catch (PaymentException e) {
    log.error("Payment failed for order {}", orderId, e);
    throw new OrderProcessingException(orderId, e);
}
```

---

## 10. Principes SOLID (les fondations de la conception)

Cinq principes pour que le code reste modifiable sans tout casser.

- **S — Single Responsibility** : une classe = une responsabilité. Controller reçoit, Service porte le métier, Repository parle à la BDD.
- **O — Open/Closed** : on ajoute un comportement via une nouvelle classe (interface/Strategy), pas en modifiant une chaîne de `if/else`.
- **L — Liskov** : une implémentation d'interface doit être remplaçable par une autre sans casser le comportement.
- **I — Interface Segregation** : des interfaces petites et ciblées, pas une interface fourre-tout de 25 méthodes.
- **D — Dependency Inversion** : on dépend d'**interfaces**, Spring injecte l'implémentation. Jamais de `new` sur une dépendance.

Exemple DIP :

❌
```java
private final EmailSender sender = new SmtpEmailSender(); // couplage en dur
```

✅
```java
private final EmailSender sender;                          // interface
public OrderService(EmailSender sender) { this.sender = sender; }
```

---

## 11. Architecture & couches (spécifique Spring)

- Découpage strict **Controller → Service → Repository**. Un Controller n'appelle jamais un Repository directement.
- **DTO en entrée/sortie d'API**, jamais l'entité JPA (`OrderResponse`, pas `Order`).
- Injection **par constructeur** uniquement, jamais `@Autowired` sur un champ.
- Packages organisés **par feature** (`order`, `user`, `payment`), pas par couche technique.

---

## 12. Validation & sécurité

- Valider **toutes** les entrées d'API avec `@Valid` + `@NotNull`, `@Size`, `@Email`…
- **Aucun secret en dur** (mot de passe, clé, token) → variables d'environnement / vault.
- Profils Spring séparés (`application-dev.yml`, `application-prod.yml`).

---

## 13. Base de données

- Migrations versionnées avec **Flyway** (ou Liquibase). Pas de modif de schéma à la main.
- **Interdit en prod** : `spring.jpa.hibernate.ddl-auto=update` → `validate` ou `none`.
- `@Transactional` au niveau **Service**.
- Attention au **N+1** (`JOIN FETCH` / `@EntityGraph`).
- **Pagination obligatoire** sur les listes (`Pageable`). Pas de `findAll()` sans limite.

---

## 14. Tests

- Toute logique métier a un **test unitaire** (JUnit 5 + Mockito).
- Cas critiques (paiement, sécurité, calculs) → **test d'intégration** (`@SpringBootTest`, Testcontainers).
- **Un bug corrigé = un test qui reproduit le bug** avant le fix.
- Nommage clair : `should_returnNotFound_when_orderDoesNotExist`.

---

## 15. Git & Pull Requests

- **Pas de push direct sur `main`.** Tout passe par une PR.
- Branches : `feature/xxx`, `fix/xxx`, `chore/xxx`.
- Commits en **Conventional Commits** : `feat:`, `fix:`, `refactor:`, `test:`, `docs:`.
- PR **≤ 400 lignes**. Au-delà, on découpe.
- **1 relecteur minimum** + **CI vert** (build, tests, format) avant merge.

### Checklist du relecteur (à cocher sur chaque PR)

- [ ] Noms clairs, pas de valeur magique
- [ ] Méthodes courtes, early return, peu de paramètres
- [ ] Pas de copier-coller (DRY)
- [ ] Null géré (Optional, pas de collection null)
- [ ] Pas de code commenté ni `System.out.println`
- [ ] Erreurs gérées (pas de catch silencieux)
- [ ] SOLID respecté (surtout SRP et DIP)
- [ ] Pas de logique métier dans le Controller, DTO utilisé, injection par constructeur
- [ ] Entrées validées, aucun secret en dur
- [ ] Tests présents (dont non-régression si bug), pas de N+1, pagination
- [ ] CI vert, PR de taille raisonnable

---

## 16. Utilisation par un agent IA

Quand ce document est fourni à un agent IA pour générer ou relire du code :
- Ces règles sont **contraignantes**, pas indicatives.
- Le code produit doit passer la checklist du §15.
- En cas de doute, choisir l'option la plus **lisible** et la plus respectueuse de la séparation en couches.
