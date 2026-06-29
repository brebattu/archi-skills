# Architectures frontend React — comparatif

> Exemples basés sur les modules product / client / order de cet ERP.

---

## Sommaire

1. [Organisation technique (anti-pattern)](#1-organisation-technique-anti-pattern)
2. [Feature-Sliced Design (FSD)](#2-feature-sliced-design-fsd)
3. [Domain Module — notre choix](#3-domain-module--vertical-slices)
4. [Feature + Repository (style Next.js)](#4-feature--repository-style-nextjs)
5. [Clean Architecture appliquée au frontend](#5-clean-architecture-appliquée-au-frontend)
6. [Tableau comparatif](#6-tableau-comparatif)

---

## 1. Organisation technique (anti-pattern)

### Philosophie

Organiser le code par **type de fichier**, pas par domaine. C'est le premier réflexe naturel quand on démarre un projet — et la première source de dette technique sur un projet qui grandit.

### Structure type

```
src/
├── components/       ← tous les composants React du projet
│   ├── ProductCard.tsx
│   ├── ClientForm.tsx
│   └── OrderTable.tsx
├── hooks/            ← tous les hooks
│   ├── useProducts.ts
│   ├── useClients.ts
│   └── useOrders.ts
├── services/         ← tous les appels API
│   ├── productService.ts
│   ├── clientService.ts
│   └── orderService.ts
├── types/            ← tous les types
│   ├── product.ts
│   ├── client.ts
│   └── order.ts
└── pages/
    ├── Products.tsx
    └── Clients.tsx
```

### Problème

Pour toucher à une fonctionnalité commande, tu navigues dans 5 dossiers différents. La fonctionnalité est **éparpillée** horizontalement. Quand le projet grossit, `components/` devient un couloir de 80 fichiers sans cohérence.

**Analogie backend** : c'est le projet Spring Boot avec un package `controllers/` qui contient tous les controllers, un package `services/` avec tous les services, etc. — une horreur à maintenir.

### Quand on le voit

Projets démarrés sans réflexion archi, tutos YouTube, premiers projets junior.

---

## 2. Feature-Sliced Design (FSD)

### Philosophie

Méthodologie frontend créée par la communauté russe (~2021), formalisée sur [feature-sliced.design](https://feature-sliced.design). L'idée centrale : organiser par **couches** avec des **règles de dépendance strictes mécaniques** — une couche ne peut importer que des couches en dessous d'elle, jamais au-dessus.

Six couches fixes, de haut en bas :

| Couche      | Rôle                                                      |
| ----------- | --------------------------------------------------------- |
| `app/`      | Bootstrap : providers, router, styles globaux             |
| `pages/`    | Composition de pages complètes                            |
| `widgets/`  | Blocs UI autonomes et complexes                           |
| `features/` | Actions utilisateur (créer, modifier, supprimer)          |
| `entities/` | Entités métier : types + API GET + composants UI basiques |
| `shared/`   | Primitives UI, utilitaires, client HTTP                   |

### Structure type

```
src/
├── app/
│   └── providers/, router.tsx, main.tsx
│
├── pages/
│   ├── products/
│   │   └── ProductListPage.tsx
│   └── clients/
│       └── ClientDetailPage.tsx
│
├── widgets/
│   ├── product-catalog/               ← liste produits + filtres + pagination
│   │   └── ui/ProductCatalog.tsx
│   └── order-form/                    ← formulaire commande complet
│       └── ui/OrderForm.tsx
│
├── features/
│   ├── product-create/
│   │   ├── ui/CreateProductForm.tsx
│   │   └── model/useCreateProduct.ts
│   ├── product-update/
│   ├── order-create/
│   │   ├── ui/OrderCreateForm.tsx
│   │   └── model/useCreateOrder.ts, orderDraftStore.ts
│   └── client-create/
│
├── entities/
│   ├── product/
│   │   ├── api/productApi.ts          ← GET /products (lecture seule)
│   │   ├── model/product.types.ts
│   │   ├── model/product.queries.ts   ← useProducts(), useProduct()
│   │   └── ui/ProductCard.tsx
│   ├── client/
│   └── order/
│
└── shared/
    ├── ui/                            ← shadcn/ui, primitives
    ├── api/httpClient.ts
    └── lib/utils.ts
```

### Règles de dépendance

```
pages    → widgets, features, entities, shared
widgets  → features, entities, shared
features → entities, shared
entities → shared
shared   → rien
```

### Avantages

- Règles mécaniques claires → difficile de faire une erreur involontaire
- Très bien documenté, communauté active
- Scalable pour de grandes équipes avec des périmètres séparés
- Force à penser en "actions utilisateur" (features) vs "données" (entities)

### Inconvénients

- `entities/` mélange types + appels API + composants UI — contre-intuitif pour un dev backend
- La frontière `features/` vs `widgets/` est floue en pratique
- Tous les domaines (product, client, order) se retrouvent mélangés dans `features/` et `widgets/` → vision horizontale, pas verticale
- Surcharge cognitive pour un solo dev
- Le nom "entities" ne correspond pas au sens DDD du terme

### Pour qui

Grandes équipes (5+ devs front) avec des périmètres fonctionnels bien séparés. Chaque squad peut posséder une couche ou un slice.

---

## 3. Domain Module — Vertical Slices

### Philosophie

Organiser le code **par domaine métier** (vertical), pas par type technique (horizontal). Chaque domaine est un **module fermé** qui contient tout ce qui le concerne. Les pages sont le seul point de composition inter-modules.

C'est l'application au frontend du principe de **bounded contexts** DDD : chaque module a une interface publique (`index.ts`) et on ne touche à ses internals que depuis l'intérieur. Les pages sont les **adapters** qui assemblent les modules.

### Structure type

```
src/
├── app/
│   ├── layouts/
│   │   ├── AppLayout.tsx              ← sidebar + navbar + logo
│   │   └── AuthLayout.tsx             ← login (sans sidebar)
│   ├── providers/
│   └── router.tsx
│
├── pages/                             ← composition, pas de logique métier
│   ├── products/
│   │   ├── ProductListPage.tsx
│   │   └── ProductDetailPage.tsx
│   ├── clients/
│   │   ├── ClientListPage.tsx
│   │   └── ClientDetailPage.tsx       ← compose client + order
│   └── orders/
│       ├── OrderListPage.tsx
│       └── OrderCreatePage.tsx
│
├── modules/
│   ├── product/
│   │   ├── api/product.api.ts         ← GET, POST, PUT, DELETE /products
│   │   ├── types/product.types.ts     ← Product, ProductCategory, ProductStatus
│   │   ├── hooks/useProducts.ts       ← TanStack Query
│   │   ├── hooks/useProduct.ts
│   │   ├── hooks/useCreateProduct.ts  ← mutation
│   │   ├── components/ProductCard.tsx
│   │   ├── components/ProductForm.tsx ← React Hook Form + Zod
│   │   ├── components/ProductTable.tsx← TanStack Table
│   │   └── index.ts                   ← interface publique du module
│   │
│   ├── client/
│   │   ├── api/client.api.ts
│   │   ├── types/client.types.ts
│   │   ├── hooks/useClients.ts
│   │   ├── hooks/useCreateClient.ts
│   │   ├── components/ClientForm.tsx
│   │   ├── components/ClientTable.tsx
│   │   └── index.ts
│   │
│   └── order/
│       ├── api/order.api.ts
│       ├── types/order.types.ts       ← Order, OrderLine, OrderStatus
│       ├── hooks/useOrders.ts         ← supporte useOrders({ clientId? })
│       ├── hooks/useCreateOrder.ts
│       ├── components/OrderForm.tsx
│       ├── components/OrderTable.tsx
│       ├── components/OrderStatusBadge.tsx
│       └── index.ts
│
└── shared/
    ├── ui/                            ← shadcn/ui + composants custom
    ├── api/httpClient.ts              ← instance Axios
    ├── types/common.types.ts          ← Pagination<T>, ApiResponse<T>
    └── utils/utils.ts
```

### Règles de dépendance

```
shared   → rien
modules  → shared + autres modules via index.ts uniquement
pages    → modules + shared
app      → pages + shared (bootstrap)
```

### Exemple de composition cross-modules

```tsx
// pages/clients/ClientDetailPage.tsx
// La page compose deux modules — c'est son seul rôle
import { ClientDetail, useClient } from "@/modules/client";
import { OrderTable, useOrders } from "@/modules/order";

export function ClientDetailPage() {
  const { clientId } = useParams();
  const { data: client } = useClient(clientId);
  const { data: orders } = useOrders({ clientId }); // filtre pré-rempli

  return (
    <>
      <ClientDetail client={client} />
      <OrderTable orders={orders} />
    </>
  );
}
```

`modules/order` ne sait pas dans quel contexte il est utilisé. C'est la page qui décide.

### Avantages

- **Domaine = dossier** : pour toucher à "commande", tu vas dans `modules/order/`. C'est tout.
- Naturel pour un dev backend DDD/hexagonal — les modules sont des bounded contexts
- `index.ts` = interface publique = port au sens hexagonal
- Facile d'extraire un module en package partagé pour une future appli mobile (React Native)
- Pas de règles artificielles à mémoriser

### Inconvénients

- Pas de règle mécanique entre modules — nécessite de la discipline (ne pas importer les internals d'un autre module)
- La frontière "ce qui va dans pages vs ce qui va dans un module" peut parfois être floue
- Moins documenté comme "méthodologie officielle" que FSD

### Pour qui

Solo dev ou petite équipe avec une culture DDD/backend. Projets à forte orientation domaine métier (ERP, CRM, outils internes).

---

## 4. Feature + Repository (style Next.js)

### Philosophie

Populaire dans l'écosystème **Next.js** avec Prisma. S'inspire directement du pattern **Repository** du DDD : la couche de données est abstraite derrière une interface, et les composants/actions ne parlent jamais directement à la base (ou à l'API) — ils passent par un repository.

Découpe en 4 couches explicites :

- **Présentation** : composants React, pages (`app/`)
- **Application** : actions/queries (Server Actions Next.js ou mutations TanStack Query)
- **Domaine** : types, schémas Zod, règles métier
- **Infrastructure** : repository (accès Prisma, fetch API)

### Structure type

```
src/
├── app/                               ← Next.js App Router (ou pages React classiques)
│   ├── (auth)/
│   └── (app)/
│       ├── products/page.tsx
│       ├── clients/page.tsx
│       └── orders/
│           ├── page.tsx
│           └── new/page.tsx
│
├── features/
│   ├── product/
│   │   ├── components/
│   │   │   ├── ProductCard.tsx
│   │   │   └── ProductForm.tsx
│   │   ├── actions/               ← Server Actions ou mutations (couche application)
│   │   │   ├── createProduct.ts
│   │   │   └── updateProduct.ts
│   │   ├── queries/               ← lecture (couche application)
│   │   │   └── getProducts.ts
│   │   ├── repositories/          ← accès données (couche infrastructure)
│   │   │   └── product.repository.ts  ← Prisma ou fetch API
│   │   └── schema/                ← types + validation Zod (couche domaine)
│   │       └── product.schema.ts
│   │
│   ├── client/
│   │   ├── components/
│   │   ├── actions/
│   │   ├── queries/
│   │   ├── repositories/
│   │   └── schema/
│   │
│   └── order/
│       ├── components/
│       ├── actions/
│       ├── queries/
│       ├── repositories/
│       └── schema/
│
└── lib/
    ├── prisma.ts                  ← instance Prisma (ou httpClient Axios)
    └── utils.ts
```

### Différence avec Domain Module

|                  | Domain Module                    | Feature + Repository                      |
| ---------------- | -------------------------------- | ----------------------------------------- |
| Contexte         | SPA React (CSR)                  | Next.js (SSR/RSC)                         |
| Accès données    | TanStack Query + API REST        | Server Actions + Prisma direct            |
| Repository       | Non (l'API backend joue ce rôle) | Oui (abstraction Prisma)                  |
| Couches internes | api / types / hooks / components | actions / queries / repositories / schema |

### Avantages

- Repository = abstraction propre, testable, interchangeable
- Très cohérent avec Next.js Server Actions (le repository est appelé côté serveur)
- 4 couches explicites rappellent l'architecture hexagonale

### Inconvénients

- Pensé pour Next.js avec accès BDD direct — moins naturel pour une SPA qui consomme une API REST
- Le "repository" d'une SPA, c'est implicitement l'API backend — ajouter une couche repository front peut être une abstraction superflue
- Plus de fichiers par feature (4 sous-dossiers vs 4 fichiers)

### Pour qui

Projets **Next.js fullstack** avec Prisma. Devs qui veulent la rigueur DDD jusqu'dans le frontend, avec Server Actions.

---

## 5. Clean Architecture appliquée au frontend

### Philosophie

Transposition directe de la **Clean Architecture** (Uncle Bob) ou de l'**architecture hexagonale** au frontend. Les dépendances pointent toujours vers le domaine, jamais vers l'extérieur. Le domaine (entités + use cases) ne connaît pas React, pas Axios, pas TanStack.

4 cercles concentriques :

```
[ Présentation (React) ]
  [ Application (use cases) ]
    [ Domaine (entités, interfaces) ]
      [ Infrastructure (API, localStorage) ]
```

### Structure type

```
src/
├── domain/                            ← pur TypeScript, zéro dépendance externe
│   ├── product/
│   │   ├── Product.ts                 ← entité, value objects
│   │   ├── ProductRepository.ts       ← interface (port)
│   │   └── ProductService.ts          ← règles métier pures
│   ├── client/
│   └── order/
│
├── application/                       ← use cases (orchestration)
│   ├── product/
│   │   ├── GetProductsUseCase.ts
│   │   └── CreateProductUseCase.ts
│   ├── client/
│   └── order/
│       └── CreateOrderUseCase.ts      ← valide stock, calcule prix, crée commande
│
├── infrastructure/                    ← adapters (implémentation des ports)
│   ├── api/
│   │   ├── ProductApiRepository.ts    ← implémente ProductRepository via Axios
│   │   ├── ClientApiRepository.ts
│   │   └── OrderApiRepository.ts
│   └── httpClient.ts
│
└── presentation/                      ← React
    ├── pages/
    │   ├── products/ProductListPage.tsx
    │   └── orders/OrderCreatePage.tsx
    ├── modules/
    │   ├── product/components/
    │   └── order/components/
    └── shared/ui/
```

### Exemple : injection de dépendance

```ts
// application/order/CreateOrderUseCase.ts
export class CreateOrderUseCase {
  constructor(
    private orderRepo: OrderRepository, // interface du domain
    private productRepo: ProductRepository
  ) {}

  async execute(dto: CreateOrderDto): Promise<Order> {
    const product = await this.productRepo.findById(dto.productId);
    if (product.stock < dto.quantity) throw new InsufficientStockError();
    return this.orderRepo.create(dto);
  }
}

// Dans un hook React (presentation)
const useCase = new CreateOrderUseCase(
  new OrderApiRepository(httpClient),
  new ProductApiRepository(httpClient)
);
```

### Avantages

- Domain complètement isolé et testable sans React, sans réseau
- Logique métier complexe (calcul de prix, validation stock) dans des use cases purs
- Familier pour un dev Java avec architecture hexagonale
- Facilitera le partage avec React Native (les use cases sont framework-agnostiques)

### Inconvénients

- **Fortement over-engineered pour un frontend** dans la majorité des cas : les "règles métier" frontend se réduisent souvent à de la validation de formulaire + affichage — le vrai métier est côté backend
- Beaucoup de boilerplate (interfaces, injection, use cases) pour appeler `GET /products`
- L'injection de dépendance n'est pas native en React — nécessite un container IoC ou des patterns maison
- Si le backend est déjà en hexagonale, dupliquer les use cases côté frontend est de la redondance

### Pour qui

Applis frontend avec une **logique métier réellement complexe côté client** (offline-first, calculs financiers lourds, règles métier partagées avec mobile). Ou équipes qui veulent un frontend testable unitairement sans mocker l'API.

---

## 6. Tableau comparatif

|                               | Technique      | FSD                 | Domain Module | Feature + Repo       | Clean Archi                   |
| ----------------------------- | -------------- | ------------------- | ------------- | -------------------- | ----------------------------- |
| **Organisation**              | Par type       | Par couche          | Par domaine   | Par domaine + couche | Par couche DDD                |
| **Familier pour dev backend** | Non            | Moyen               | Oui           | Oui                  | Oui                           |
| **Règles de dépendance**      | Aucune         | Mécaniques strictes | Convention    | Convention           | Strictes (DI)                 |
| **Boilerplate**               | Faible         | Moyen               | Faible        | Moyen                | Élevé                         |
| **Scalabilité**               | Faible         | Élevée              | Bonne         | Bonne                | Élevée                        |
| **Adapté solo dev**           | Non            | Moyen               | Oui           | Oui                  | Non                           |
| **Contexte idéal**            | Petits projets | Grandes équipes     | SPA métier    | Next.js fullstack    | Logique métier front complexe |

---
