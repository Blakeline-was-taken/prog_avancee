# **Introduction**

Ce rapport explore l'utilisation de la méthode de Monte Carlo (MC) pour calculer π en exploitant le parallélisme sur des architectures à mémoire partagée et distribuée. Après une présentation de l’algorithme séquentiel, nous étudions des variantes parallèles (itération parallèle, maître-esclave) et analysons deux implémentations Java.

Enfin, nous étendons l’étude aux environnements à mémoire distribuée et au parallélisme sur plusieurs machines, en évaluant les performances des différentes approches. Ce travail vise à fournir une vue synthétique et claire des stratégies et résultats obtenus.

Ce Rapport a été en partie rédigé par ChatGPT, dans le but de le simplifier et de le rendre le plus clair et concis possible.

# **I. Monte Carlo pour calculer π**

La méthode de Monte Carlo repose sur une estimation probabiliste pour approximer π à partir de tirages aléatoires.

Soit $A_{\text{quartD}}$ l’aire d’un quart de disque de rayon $r = 1$ :  
$$A_{\text{quartD}} = \frac{\pi r^2}{4} = \frac{\pi}{4}$$

Le quart de disque est inscrit dans un carré de côté $r = 1$, dont l’aire est :  
$$A_c = r^2 = 1$$

On considère un point $X_p(x_p, y_p)$ généré aléatoirement dans ce carré, où $x_p$ et $y_p$ suivent la loi uniforme $U(]0,1[)$.

La probabilité que $X_p$ appartienne au quart de disque est donnée par :  
$$P = \frac{A_{\text{quartD}}}{A_c} = \frac{\pi}{4}$$

![Illustration_MC](img/MC_Graphique.png)

Pour estimer cette probabilité, on effectue $n_{\text{tot}}$ tirages aléatoires. Soit $n_{\text{cible}}$ le nombre de points qui satisfont la condition $x_p^2 + y_p^2 \leq 1$, c’est-à-dire les points situés dans le quart de disque.

Si $n_{\text{tot}}$ est suffisamment grand, par la loi des grands nombres, la fréquence observée $n_{\text{cible}} / n_{\text{tot}}$ converge vers la probabilité $P$, soit :  
$$P = \frac{n_{\text{cible}}}{n_{\text{tot}}} \approx \frac{\pi}{4}$$

On peut ainsi en déduire une approximation de π :  
$$\pi \approx 4 \cdot \frac{n_{\text{cible}}}{n_{\text{tot}}}$$

Ainsi, plus $n_{\text{tot}}$ augmente, plus l'estimation de π se précise.

## **II. Algorithme et parallélisation**

### **A. Itération parallèle**

L’algorithme de Monte Carlo peut être parallélisé en distribuant les tirages sur plusieurs tâches indépendantes. Voici l'algorithme séquentiel de base :

#### **Algorithme de base**

```
n_cible = 0;
for (p = 0; n_tot > 0; n_tot--) {
    x_p = rand();  // Générer un nombre aléatoire entre ]0,1[
    y_p = rand();
    if ((x_p * x_p + y_p * y_p) < 1) {
        n_cible++;
    }
}
pi = 4 * n_cible / n_tot;
```

Dans cette version, tout est exécuté séquentiellement. Pour paralléliser ce code, il faut identifier les tâches du programme et leurs dépendances afin de déterminer celles qui peuvent être exécutées en parallèle.

#### **Tâches identifiées**

1. **Tâche principale (T0) :** Tirer et compter les `n_tot` points.
    - **Sous-tâche T0p1 :** Générer `x_p` et `y_p`.
    - **Sous-tâche T0p2 :** Vérifier si `x_p^2 + y_p^2 < 1` et incrémenter `n_cible` si c'est le cas.

2. **Tâche secondaire (T1) :** Calculer `pi` une fois `n_cible` déterminé :
   `pi = 4 * n_cible / n_tot;`

#### **Dépendances entre tâches**

- **T1 dépend de T0 :** `pi` ne peut être calculé qu’après la collecte de `n_cible`.
- **T0p2 dépend de T0p1 :** Un point doit être généré avant de vérifier s’il appartient au quart de disque.
- **Indépendances parallèles :**
    - Les `T0p1` (génération des points) peuvent être exécutées en parallèle, car chaque point est indépendant.
    - Les `T0p2` (vérifications et comptage) peuvent également être parallélisées. Cependant, cela nécessite une gestion spécifique des accès à `n_cible`, car plusieurs threads peuvent modifier cette variable simultanément.

Ainsi, on a identifié une section critique, et une ressource partagée :

- **Section critique :** `n_cible++;`

  Plusieurs threads peuvent tenter de modifier `n_cible` en même temps.

- **Ressource partagée :**

  L’accès à `n_cible` doit être protégé pour éviter des conflits d'accès simultané.

#### **Algorithme parallèle**

L'algorithme parallèle repose sur une fonction dédiée `TirerPoint()` pour générer et évaluer les points :

```
function TirerPoint() {
    x_p = rand();  // Générer un nombre aléatoire entre ]0,1[
    y_p = rand();
    return ((x_p * x_p + y_p * y_p) < 1);
}

n_cible = 0;
parallel for (p = 0; n_tot > 0; n_tot--) {
    if (TirerPoint()) {
        n_cible++;
    }
}
pi = 4 * n_cible / n_tot;
```

La fonction `TirerPoint()` n’a pas de dépendance, chaque tirage peut être exécuté indépendamment sur plusieurs threads.

### **B. Master/Worker**

Dans le paradigme Master/Worker, le travail est divisé en plusieurs unités indépendantes, chacune étant attribuée à un processus (ou thread) dit **"Worker"**. Ici, chaque Worker exécute une partie des tirages de manière itérative, et le résultat est ensuite combiné par un processus maître, dit **"Master"**.

![Execution_MW](img/Execution_MW.png)

#### **Algorithme Master/Worker**

```
function TirerPoint() {
    x_p = rand();  // Générer un nombre aléatoire entre ]0,1[
    y_p = rand();
    return ((x_p * x_p + y_p * y_p) < 1);
}

function MCWorker(n_charge) {
    n_cible_partiel = 0;
    for (p = 0; n_charge > 0; n_charge--) {
        if (TirerPoint()) {
            n_cible_partiel += 1;
        }
    }
    return n_cible_partiel;
}

n_charge = n_tot / n_workers;
ncibles = [NULL * n_workers];
parallel for (worker = 0; worker < n_workers; worker++) {
    ncibles[worker] = MCWorker(n_charge);
}
n_cible = sum(ncibles);
pi = 4 * n_cible / n_tot;
```

#### **Explications**

1. **Principe :**
    - Le travail total (`n_tot` tirages) est réparti uniformément entre les `n_workers` processus ou threads.
    - Chaque Worker exécute la fonction `MCWorker()` sur son propre sous-ensemble de points (`n_charge` tirages).

2. **Ressources partagées et sections critiques :**  
   Contrairement à l'itération parallèle, chaque Worker maintient son propre compteur local (`n_cible_partiel`). Ainsi chaque Worker peut travailler de son côté sans entrer dans des conflits d’accès lors de l’incrémentation.

3. **Tableau `ncibles` :** La seule ressource critique ici est un tableau, afin d'éviter les conflits d'accès. La section critique est ainsi prise en charge, car chaque Worker n'a besoin d'accéder qu'à sa case mémoire dédiée. 

4. **Agrégation :**  
   Une fois les calculs des Workers terminés, le Master (programme principal) combine leurs résultats (somme de `ncibles`) pour obtenir la valeur totale de `n_cible`, et calcule ensuite π.

#### **Avantages du modèle Master/Worker**

- **Réduction des conflits :** Chaque Worker travaille sur des données locales, réduisant le besoin de synchronisation.
- **Scalabilité :** La charge est répartie uniformément entre les Workers, ce qui peut améliorer les performances sur un grand nombre de threads. Nous testerons cela dans la **IVème partie**.
- **Modularité :** La structure facilite l’adaptation à des architectures distribuées, où les Workers peuvent s’exécuter sur des machines distinctes. Nous testerons cela dans la **VIIème partie**.

## **III. Mise en œuvre sur Machine**

Nous allons maintenant étudier deux implémentations pratiques de la méthode de Monte Carlo pour le calcul de π. L'objectif est d'analyser leur structure et leur approche de parallélisation :

1. Identifier le **modèle de programmation parallèle** utilisé dans chaque code ainsi que le **paradigme suivi** (itération parallèle ou Master/Worker).
2. Vérifier si ces implémentations correspondent aux algorithmes proposés en **partie II**.

Nous effectuerons ensuite dans la **partie IV** une analyse détaillée de chaque code en évaluant leur scalabilité forte et faible.

### **A. Analyse Assignment102**

L'implémentation **Assignment102** utilise l'API Concurrent pour paralléliser les calculs nécessaires à l'estimation de π avec la méthode de Monte Carlo. Voici les points principaux analysés :

#### **Structure et API utilisée**

![DiagrammeClasse_Assignment102](img/DiagrammeClasse_Assignment102.png)

- **Gestion des threads :**
    - Le code utilise `ExecutorService` avec un **pool de threads adaptatif** (`newWorkStealingPool`), exploitant efficacement les cœurs disponibles sur la machine.
    - Chaque tirage (génération d’un point aléatoire) est exécuté dans une tâche indépendante via `Runnable`.
- **Synchronisation avec AtomicInteger :**
    - La variable partagée `nAtomSuccess`, qui compte le nombre de points dans le quart de disque, est protégée par un **compteur atomique** (`AtomicInteger`) pour éviter les conflits d’accès entre threads.

#### **Modèle de programmation parallèle et paradigme**

- **Modèle utilisé :** Itération parallèle. Chaque tirage correspond à une tâche indépendante soumise au pool de threads.
- **Paradigme :** Le code suit le modèle d’**itération parallèle** décrit dans la partie II.A. Chaque tâche effectue un tirage de manière indépendante, sans dépendances entre elles.

#### **Lien avec notre pseudo-code**

L'implémentation correspond globalement au pseudo-code d'**itération parallèle** proposé, avec les adaptations suivantes :
- Le compteur `n_cible` est remplacé par un `AtomicInteger` pour gérer les sections critiques.
- Le découplage des threads est entièrement géré par l’API `ExecutorService`.

#### **Limites et optimisation possibles**

1. **Impact des accès atomiques :**
    - Chaque incrémentation de `nAtomSuccess` via `incrementAndGet()` est coûteuse en termes de synchronisation, ce qui peut provoquer des **goulots d’étranglement**. On parle presque de **75% du temps d'exécution** pouvant être consacré uniquement à la gestion des accès atomiques.

2. **Optimisation possible :**
    - **Regroupement local :** Chaque thread pourrait maintenir un compteur local pour `nAtomSuccess`, et ces valeurs pourraient être agrégées à la fin, réduisant la contention.
    - **Filtrage des points hors cible :** Plutôt que d'incrémenter le compteur atomique à chaque point DANS la cible, on pourrait le faire quand ils sont en dehors, et simplement prendre la valeur inverse de la variable pour le calcul de Pi.

En conclusion, bien que cette implémentation soit correcte et facilement compréhensible, elle est limitée par des problèmes d’optimisation liés à la synchronisation atomique.

### **B. Analyse Pi.java**

L'implémentation **Pi.java** repose sur l'utilisation des **Futures** et des **Callables** pour paralléliser le calcul de π à l'aide de la méthode de Monte Carlo.

#### **Qu’est-ce qu’un `Future` ?**

Un `Future` est un conteneur pour un résultat calculé de manière asynchrone. Il permet :
- **De soumettre une tâche :** Lorsqu'une tâche est exécutée par un thread, son résultat est encapsulé dans un `Future`.
- **De récupérer le résultat :** L'appel à `get()` permet de récupérer la valeur, mais bloque jusqu'à ce que le calcul soit terminé. Cela introduit une **barrière implicite** qui synchronise les résultats des différents threads.
- **De vérifier l'état d'exécution :** Un `Future` peut également indiquer si la tâche est terminée ou si elle a échoué.

Ici, les `Futures` permettent de gérer la synchronisation entre les threads de manière simple et efficace, en garantissant que chaque résultat partiel est prêt avant l'agrégation.

![DiagrammeClasse_Pi](img/DiagrammeClasse_Pi.png)

#### **Modèle de programmation parallèle et paradigme**

- **Modèle utilisé :** Master/Worker. Le **Master** crée des `Workers` (tâches) pour effectuer les calculs de Monte Carlo, et il regroupe les résultats des `Futures` pour produire le résultat final.
- **Paradigme :** Basé sur les tâches avec gestion explicite des tâches via des `Callables`.

#### **Structure et API utilisée**

1. **Parallélisation avec des `Callables` :**
    - Chaque `Worker` est implémenté comme un `Callable<Long>` qui effectue un sous-ensemble du calcul total, à savoir déterminer le nombre de points tombant dans le quart de disque pour un certain nombre d'itérations.
    - Ces `Callables` sont ensuite exécutés par un pool de threads fixe (`FixedThreadPool`), permettant leur exécution en parallèle.

2. **Gestion des résultats avec des `Futures` :**
    - Lorsqu'un `Callable` est soumis au pool de threads, il renvoie un objet `Future<Long>` qui représente un résultat futur.
    - L'appel à `Future.get()` bloque le thread principal jusqu'à ce que le calcul associé au `Callable` soit terminé.
    - Une fois tous les résultats collectés, ils sont agrégés pour calculer la valeur finale de π.

#### **Lien avec notre pseudo-code**

L'algorithme suit fidèlement la logique **Master/Worker** définie en partie II.B :
- **Master :** Correspond à la classe `Master`, qui distribue les tâches aux `Workers` et agrège leurs résultats.
- **Workers :** Implémentés via les `Callables`, chaque `Worker` exécute localement la méthode `MCWorker()` de notre pseudo-code.
- **Division équitable :** Le `Master` divise uniformément les tirages (`n_charge`) entre les différents `Workers`.

#### **Comparaison avec Assignment102**

1. **Isolation des calculs :** Chaque `Worker` calcule ses résultats localement sans dépendre d’une variable partagée, ce qui élimine le besoin d’outils comme `AtomicInteger`.

2. **Moins de synchronisation coûteuse :** Le recours aux `Futures` permet de retarder la synchronisation jusqu'à l'agrégation finale, réduisant les coûts liés à l'accès concurrent.

3. **Efficacité :** En minimisant la gestion des ressources partagées et en optimisant l'utilisation des threads, cette implémentation est mieux adaptée aux environnements multithread, en particulier sur des machines multicœurs.

On peut donc s'attendre à de meilleures performances qu'`Assignment102` au moment au test de performances, particulièrement avec un grand nombre de points et de threads.

## IV. Évaluations et tests de performances

La partie suivante vient directement d'un autre rapport que j'écris en parallèle de celui-ci, qui concerne le module de Qualité de Développement en troisième année de BUT informatique.

L'ordinateur qui a réalisé ces calculs possède les specs suivantes :
- **Processeur :** 11th Gen Intel(R) Core(TM) i7-11800H @ 2.30GHz
- 8 cœurs physiques
- 16 cœurs logiques

Notez que les résultats des tests qui suivent ne seront pas les mêmes selon l'architecture matérielle sur lesquels ils ont été effectués.

### **A. Programme de calcul de performance**

Le script PerformanceTester.java teste différentes implémentations de Monte Carlo pour calculer π en s'appuyant sur une interface standardisée (`MonteCarloImplementation`). Chaque implémentation doit fournir deux méthodes : `execute(int totalPoints, int numCores)` et `getName()`.

Il récupère les données de tests (nombre de cœurs, de points, et répétitions) dans un fichier csv, effectue les tests sur chaque implémentation puis enregistre les résultats (temps d'exécution, approximation de π, erreur relative) dans un fichier `resultats.csv`.

L'outil est modulaire, permettant d'ajouter facilement de nouvelles versions de Monte Carlo à tester, tout en automatisant l'évaluation des performances pour différents scénarios.

### **B. Tests de Scalabilité**

Nous allons réaliser les mêmes tests sur tous les codes, afin de pouvoir les comparer plus efficacement.  On compare les performances des différents codes en termes de scalabilité, en évaluant leur capacité à s'adapter à une augmentation du nombre de cœurs, selon deux approches : **scalabilité forte** et **scalabilité faible**.

#### **1. Scalabilité Forte**

La **scalabilité forte** mesure la capacité d'un programme à réduire son temps d'exécution lorsqu'on augmente le nombre de cœurs, tout en maintenant la charge de travail totale constante. Elle évalue comment le programme exploite efficacement les ressources supplémentaires.

On l'évalue à l'aide du **speedup**, défini comme :

```
Speedup = Temps_1_cœur / Temps_N_cœurs
```

Un speedup idéal est linéaire (e.g., un gain proportionnel au nombre de cœurs).

![Speedup_Scalabilité_Forte](img/SpeedupScalabiliteForte.png)

Pour la scalabilité forte, on maintient la charge totale constante (le nombre de points) tout en augmentant le nombre de processeurs. Les expériences se répartissent comme suit :

| **Nombre de processeurs** | **Nombre total de points** | **Points par processeur** |  
|---------------------------|----------------------------|---------------------------|  
| 1                         | 1,000,000                  | 1,000,000                 |  
| 2                         | 1,000,000                  | 500,000                   |  
| 4                         | 1,000,000                  | 250,000                   |  
| 8                         | 1,000,000                  | 125,000                   |  
| 16                        | 1,000,000                  | 62,500                    |  
| 1                         | 10,000,000                 | 10,000,000                |  
| 2                         | 10,000,000                 | 5,000,000                 |  
| 4                         | 10,000,000                 | 2,500,000                 |  
| 8                         | 10,000,000                 | 1,250,000                 |  
| 16                        | 10,000,000                 | 625,000                   |  
| 1                         | 100,000,000                | 100,000,000               |  
| 2                         | 100,000,000                | 50,000,000                |  
| 4                         | 100,000,000                | 25,000,000                |  
| 8                         | 100,000,000                | 12,500,000                |  
| 16                        | 100,000,000                | 6,250,000                 |  

#### **2. Scalabilité Faible**

La **scalabilité faible** évalue la capacité d'un programme à maintenir un temps d'exécution constant lorsqu'on augmente proportionnellement le nombre de cœurs **et** la charge de travail totale. Cela simule un scénario où chaque cœur traite une part fixe de travail supplémentaire.

Elle est également mesurée à l'aide du **speedup**, calculé comme dans la scalabilité forte, mais ici avec une charge croissante. Ici, le speedup idéal est constant, une droite qui reste horizontale.

Pour la scalabilité faible, on augmente proportionnellement la charge totale en fonction du nombre de processeurs, de façon à maintenir une charge constante par processeur. Les expériences se répartissent comme suit :

| **Nombre de processeurs** | **Nombre total de points** | **Points par processeur** |  
|---------------------------|----------------------------|---------------------------|  
| 1                         | 1,000,000                  | 1,000,000                 |  
| 2                         | 2,000,000                  | 1,000,000                 |  
| 4                         | 4,000,000                  | 1,000,000                 |  
| 8                         | 8,000,000                  | 1,000,000                 |  
| 16                        | 16,000,000                 | 1,000,000                 |  
| 1                         | 10,000,000                 | 10,000,000                |  
| 2                         | 20,000,000                 | 10,000,000                |  
| 4                         | 40,000,000                 | 10,000,000                |  
| 8                         | 80,000,000                 | 10,000,000                |  
| 16                        | 160,000,000                | 10,000,000                |  
| 1                         | 100,000,000                | 100,000,000               |  
| 2                         | 200,000,000                | 100,000,000               |  
| 4                         | 400,000,000                | 100,000,000               |  
| 8                         | 800,000,000                | 100,000,000               |  
| 16                        | 1,600,000,000              | 100,000,000               |  

### **C. Résultats Assignment102**

Pour évaluer la scalabilité de l'implémentation `Assignment102`, nous avons modifié le code pour permettre de fixer le nombre de processeurs utilisés via l'initialisation de la classe `PiMonteCarlo`. Cela remplace l'utilisation dynamique de `Runtime.getRuntime().availableProcessors()` et nous permet de limiter précisément le nombre de cœurs pour chaque test.

Voici les résultats obtenus lors des tests de scalabilité forte, répétés 5 fois pour avoir une moyenne :

#### **Tableau des résultats de scalabilité forte**

| **Nombre de cœurs** | **Points lancés** | **Points par cœur** | **Temps d'exécution (ms)** | **Approximation de π** | **Erreur**          |  
|---------------------|-------------------|---------------------|----------------------------|------------------------|---------------------|
| 1                   | 1,000,000         | 1,000,000           | 92.0                       | 3.1414976              | 3.03 × 10⁻⁵         |  
| 2                   | 1,000,000         | 500,000             | 84.2                       | 3.1398992              | 5.39 × 10⁻⁴         |  
| 4                   | 1,000,000         | 250,000             | 92.6                       | 3.14152                | 2.31 × 10⁻⁵         |  
| 8                   | 1,000,000         | 125,000             | 126.2                      | 3.1420408              | 1.43 × 10⁻⁴         |  
| 16                  | 1,000,000         | 62,500              | 127.2                      | 3.1411                 | 1.57 × 10⁻⁴         |  
| 1                   | 10,000,000        | 10,000,000          | 836.8                      | 3.14186112             | 8.55 × 10⁻⁵         |  
| 2                   | 10,000,000        | 5,000,000           | 206.4                      | 3.14125008             | 1.09 × 10⁻⁴         |  
| 4                   | 10,000,000        | 2,500,000           | 819.8                      | 3.14155624             | 1.16 × 10⁻⁵         |  
| 8                   | 10,000,000        | 1,250,000           | 869.0                      | 3.1418644              | 8.65 × 10⁻⁵         |  
| 16                  | 10,000,000        | 625,000             | 925.4                      | 3.14146304             | 4.13 × 10⁻⁵         |  
| 1                   | 100,000,000       | 100,000,000         | 8,788.0                    | 3.141590984            | 5.31 × 10⁻⁷         |  
| 2                   | 100,000,000       | 50,000,000          | 8,358.2                    | 3.141534816            | 1.84 × 10⁻⁵         |  
| 4                   | 100,000,000       | 25,000,000          | 8,743.0                    | 3.141645944            | 1.70 × 10⁻⁵         |  
| 8                   | 100,000,000       | 12,500,000          | 8,728.4                    | 3.141609744            | 5.44 × 10⁻⁶         |  
| 16                  | 100,000,000       | 6,250,000           | 8,625.2                    | 3.1416518              | 1.88 × 10⁻⁵         |  

En utilisant un programme Python pour calculer le speedup et générer un graphique, on obtient la courbe suivante :

![Scalabilité forte Assignment102](plot/scalabilite_forte_Assignment102_10000000.png)

L'analyse des résultats montre que le speedup commence à 1, puis baisse avant de stagner sous 1. Cette diminution de performance peut être due à plusieurs facteurs :

- **Surcharge de synchronisation** : L'utilisation de `AtomicInteger` pour la gestion des ressources partagées introduit une latence, ralentissant les threads à mesure que leur nombre augmente.
- **Overhead lié aux threads** : La gestion des threads devient coûteuse lorsque le nombre de cœurs dépasse un certain seuil, annulant les gains de parallélisation.
- **Tâches trop petites** : L'overhead de la synchronisation devient plus important que les bénéfices de la parallélisation avec de petites charges de travail par processeur.

Ces facteurs expliquent la baisse du speedup et indiquent que l'implémentation n'est pas optimale au-delà d'un certain nombre de cœurs.

#### **Tableau des résultats de scalabilité faible**

Voici le tableau des résultats pour les tests de scalabilité faible d'Assignment102, répétés 5 fois pour avoir une moyenne :

| Nombre de cœurs | Points lancés | Points par cœur | Temps d'exécution (ms) | Approximation de PI | Erreur  |
|-----------------|---------------|-----------------|------------------------|---------------------|---------|
| 1               | 1,000,000     | 1,000,000       | 120.2                  | 3.1432696           | 5.34e-4 |
| 2               | 2,000,000     | 1,000,000       | 205.0                  | 3.141348            | 7.79e-5 |
| 4               | 4,000,000     | 1,000,000       | 354.6                  | 3.1415638           | 9.18e-6 |
| 8               | 8,000,000     | 1,000,000       | 773.6                  | 3.1415113           | 2.59e-5 |
| 16              | 16,000,000    | 1,000,000       | 1456.8                 | 3.1415671           | 8.13e-6 |
| 1               | 10,000,000    | 10,000,000      | 943.8                  | 3.14194448          | 1.12e-4 |
| 2               | 20,000,000    | 10,000,000      | 1826.0                 | 3.14176164          | 5.38e-5 |
| 4               | 40,000,000    | 10,000,000      | 3875.0                 | 3.14177182          | 5.70e-5 |
| 8               | 80,000,000    | 10,000,000      | 10112.6                | 3.14151361          | 2.52e-5 |
| 16              | 160,000,000   | 10,000,000      | 20315.6                | 3.141623355         | 9.77e-6 |
| 1               | 100,000,000   | 100,000,000     | 11313.8                | 3.141555048         | 1.20e-5 |

### Observations

Les tests se limitent ici pour `Assignment102` en raison de problèmes d'optimisation, causant une erreur `OutOfMemory` pour des charges de travail plus importantes. Cette limitation est un indicateur de la mauvaise gestion des ressources dans cette implémentation, notamment pour de très grandes quantités de points.

Voici le speedup calculé par le même programme python pour la scalabilité faible :

![Scalabilité faible Assignment102](plot/scalabilite_faible_Assignment102_10000000.png)

Comme on peut l'observer, la scalabilité est loin d'être linéaire. Elle semble décroître proportionnellement au nombre de points ajoutés. En effet, chaque fois que l'on double le nombre de points, le speedup est réduit de moitié.

Ce résultat n'est toutefois pas surprenant, étant donné celui observé lors du test de scalabilité forte. Le speedup était presque linéaire, ce qui suggère que l'ajout de processus a un impact quasi-inexistant sur la performance du programme. Par conséquent, doubler le nombre de points entraîne logiquement un temps d'exécution deux fois plus long.

### **D. Résultats Pi.Java**

Aucune modification n'a été nécessaire pour cette classe, qui intègre déjà une option permettant de limiter le nombre de workers.

#### **Tableau des résultats de scalabilité forte**

Voici les résultats obtenus lors des tests de scalabilité forte, répétés 5 fois pour calculer une moyenne :

| Nombre de cœurs | Points lancés | Points par cœur | Temps d'exécution (ms) | Approximation de PI | Erreur            |
|-----------------|---------------|-----------------|------------------------|---------------------|-------------------|
| 1               | 1,000,000     | 1,000,000       | 56.8                   | 3.1431048           | 4.81 × 10⁻⁴       |
| 2               | 1,000,000     | 500,000         | 27.0                   | 3.141724            | 4.18 × 10⁻⁵       |
| 4               | 1,000,000     | 250,000         | 14.4                   | 3.1408              | 2.52 × 10⁻⁴       |
| 8               | 1,000,000     | 125,000         | 11.6                   | 3.1418616           | 8.56 × 10⁻⁵       |
| 16              | 1,000,000     | 62,500          | 8.0                    | 3.1423224           | 2.32 × 10⁻⁴       |
| 1               | 10,000,000    | 10,000,000      | 414.0                  | 3.14151048          | 2.62 × 10⁻⁵       |
| 2               | 10,000,000    | 5,000,000       | 217.8                  | 3.1416816           | 2.83 × 10⁻⁵       |
| 4               | 10,000,000    | 2,500,000       | 122.4                  | 3.14141312          | 5.71 × 10⁻⁵       |
| 8               | 10,000,000    | 1,250,000       | 68.4                   | 3.14099672          | 1.90 × 10⁻⁴       |
| 16              | 10,000,000    | 625,000         | 59.0                   | 3.14134416          | 7.91 × 10⁻⁵       |
| 1               | 100,000,000   | 100,000,000     | 4335.0                 | 3.141607864         | 4.84 × 10⁻⁶       |
| 2               | 100,000,000   | 50,000,000      | 2421.2                 | 3.141605256         | 4.01 × 10⁻⁶       |
| 4               | 100,000,000   | 25,000,000      | 1250.4                 | 3.141652968         | 1.92 × 10⁻⁵       |
| 8               | 100,000,000   | 12,500,000      | 677.4                  | 3.141578304         | 4.57 × 10⁻⁶       |
| 16              | 100,000,000   | 6,250,000       | 412.0                  | 3.141576256         | 5.22 × 10⁻⁶       |

La courbe suivante représente le speedup obtenu :

![Scalabilité forte Pi.java](plot/scalabilite_forte_Pi.java_100000000.png)

Comme on peut le constater, la courbe suit une trajectoire presque linéaire sur une large plage de points, avant de légèrement dévier au-delà de 8 cœurs. Malgré cela, le speedup reste croissant, ce qui témoigne d'une parallélisation efficace.

Avec 8 cœurs physiques (avec 2 coeurs logiques chacun), l'implémentation atteint une performance équivalente à environ 11 cœurs logiques. Cela confirme l'efficacité de la parallélisation dans ce code.

#### **Tableau des résultats de scalabilité faible**

| Nombre de cœurs | Points lancés  | Points par cœur | Temps d'exécution (ms) | Approximation de PI | Erreur            |
|-----------------|----------------|-----------------|------------------------|---------------------|-------------------|
| 1               | 1,000,000      | 1,000,000       | 63.0                   | 3.1423984           | 2.56 × 10⁻⁴       |
| 2               | 2,000,000      | 1,000,000       | 56.2                   | 3.1412836           | 9.84 × 10⁻⁵       |
| 4               | 4,000,000      | 1,000,000       | 64.6                   | 3.1418568           | 8.41 × 10⁻⁵       |
| 8               | 8,000,000      | 1,000,000       | 109.6                  | 3.1417325           | 4.45 × 10⁻⁵       |
| 16              | 16,000,000     | 1,000,000       | 156.4                  | 3.14142355          | 5.38 × 10⁻⁵       |
| 1               | 10,000,000     | 10,000,000      | 530.8                  | 3.14203488          | 1.41 × 10⁻⁴       |
| 2               | 20,000,000     | 10,000,000      | 552.0                  | 3.14175008          | 5.01 × 10⁻⁵       |
| 4               | 40,000,000     | 10,000,000      | 534.4                  | 3.14163064          | 1.21 × 10⁻⁵       |
| 8               | 80,000,000     | 10,000,000      | 579.4                  | 3.14150764          | 2.71 × 10⁻⁵       |
| 16              | 160,000,000    | 10,000,000      | 776.2                  | 3.1415749           | 5.65 × 10⁻⁶       |
| 1               | 100,000,000    | 100,000,000     | 4662.4                 | 3.141613336         | 6.58 × 10⁻⁶       |
| 2               | 200,000,000    | 100,000,000     | 5105.6                 | 3.14153096          | 1.96 × 10⁻⁵       |
| 4               | 400,000,000    | 100,000,000     | 5256.0                 | 3.141570872         | 6.93 × 10⁻⁶       |
| 8               | 800,000,000    | 100,000,000     | 7791.6                 | 3.141575614         | 5.42 × 10⁻⁶       |
| 16              | 1,600,000,000  | 100,000,000     | 8710.6                 | 3.141579264         | 4.26 × 10⁻⁶       |

La courbe suivante illustre le speedup observé :

![Scalabilité faible Pi.java](plot/scalabilite_faible_Pi.java_100000000.png)

On peut voir que le speedup **décroît** lentement au fur et à mesure que le nombre de processeurs augmente. Bien que cette décroissance soit bien plus modérée que dans le cas d'Assignment102, le speedup passe tout de même de **1** (avec un seul processeur) à environ **0,75** avec 16 processeurs.

Cette baisse indique que le code `Pi.java` perd en efficacité parallèle avec l'augmentation des ressources disponibles, mais cette perte reste contenue. Cela pourrait être lié à des surcoûts croissants liés à la gestion des threads ou à une saturation progressive de la capacité à paralléliser les calculs supplémentaires de manière optimale. Cela reste néanmoins un résultat globalement satisfaisant comparé à Assignment102, où la scalabilité chute bien plus rapidement.

## **V. Mise en œuvre en mémoire distribuée**

Les analyses précédentes ont démontré que le paradigme Master/Worker est plus efficace en termes de parallélisation comparé à l’approche adoptée par *Assignment102*. Nous souhaitons maintenant porter cet algorithme sur une architecture à mémoire distribuée.

Conformément au cours, le paradigme Master/Worker peut être vu comme l'opposé du paradigme Client/Serveur. Dans ce modèle, le *Maître* agit comme un client, tandis que les *Workers* jouent le rôle de serveurs.

On va donc étudier une implémentation reposant sur ce paradigme, où les échanges se font via des sockets Java. Le code fourni fonctionne déjà, mais il ne contient pas encore la partie dédiée au calcul de Pi.

![Diagramme d'exécution du code](img/Execution_MW_Sockets.png)

Dans cette architecture, un Master Socket est utilisé pour initialiser l'expérience Monte Carlo. Ce dernier répartit le travail entre un certain nombre de Worker Sockets en leur attribuant le nombre de points à traiter. Chaque Worker Socket réalise alors ses calculs (actuellement, il ne fait que renvoyer une valeur approximative) et renvoie son résultat au Master.

![Diagramme de classes UML](img/DiagrammeClasse_Sockets.png)

Les échanges entre le Master Socket et les Worker Sockets reposent sur les classes de la bibliothèque `java.net`. Les flux de données sont gérés par `InputStreamReader` et `OutputStreamWriter`. Les classes `PrintWriter` et `BufferedWriter` sont utilisées pour envoyer des messages, tandis que `BufferedReader` permet de les lire.

Pour exécuter le programme, il faut lancer différentes instances de WorkerSocket et MasterSocket. Dans le cas de WorkerSocket, il est nécessaire de donner en argument au lancement le port sur lequel il doit écrire/lire les flux de données. Et dans le cas de MasterWorker, au démarrage, un prompt demande d'entrer les ports des Workers à utiliser.

### **A. Implémentation calcul par méthode**

![Nouvelle classe WorkerSocket](img/NewWorkerSocket.png)

Pour implémenter la partie calcul de Pi, une méthode `performMonteCarloComputation` a été ajoutée. Cette méthode est appelée lorsque le Worker reçoit une demande de calcul : il traite sa part de points Monte Carlo avant de transmettre le résultat au Master.

### **B. Implémentation calcul en utilisant Pi.java**

Bien que l'ajout d'une méthode dédiée au calcul Monte Carlo soit une approche fonctionnelle, une alternative plus intéressante consiste à réutiliser le programme que nous avons déjà conçu, à savoir l'algorithme de *Pi.java*. Ainsi, la classe *Master* de *Pi.java* est intégrée directement dans le *WorkerSocket*.

Cette approche donne lieu à une architecture *Master/Worker* multi-niveaux :

- Au premier niveau, un Master Socket répartit les tâches entre plusieurs Worker Sockets sur une architecture à mémoire distribuée.
- Au second niveau, chaque Worker Socket devient le *Master* d'une architecture *Master/Worker* à mémoire partagée, grâce à l'implémentation déjà existante de *Pi.java*.

Ce modèle, appelé *Programmation Multi-Niveaux*, exploite les avantages de deux types de parallélisme : le parallélisme sur mémoire distribuée au niveau supérieur et le parallélisme sur mémoire partagée au niveau inférieur.

Nous explorerons les possibilités qu'une telle architecture nous offre dans la **partie VII**, mais pour le moment, il nous faut évaluer ce nouveau code comme nous l'avons fait avec *Assignment102* et *Pi.java*.