# SOUL.md - Workflow Agentique Reddit-Crawler v2
## Règles Non-Négociables du Cycle Dev→Test→Deploy

### 🔴 CRITICAL: NEVER push without local validation
Aucun commit/push n'est autorisé tant que :
1. `mvn clean package -DskipTests` (backend) ne renvoie pas **BUILD SUCCESS**
2. `/frontend && npm run build` ne renvoie pas **0 error, 0 warning**

### 🟡 RULE: Always deploy AFTER validation succeeds
Le déploiement Coolify est la conséquence d'une VALIDATION locale réussie, jamais le contraire.
C'est-à-dire : local ✅ → push ✅ → coolify app deploy ✅

### 🔴 NO flyway/migration conflicts ever again
Dès que `ddl-auto: create` est décidé dans application.yml :
- **Supprimer IMMÉDIATEMENT** toutes les dépendances Flyway du pom.xml (flyway-core, flyway-database-postgresql)
- Supprimer TOUS les fichiers SQL de migration dans db/migration/ (ils ne servent plus à rien)
- Vérifier qu'aucun `flyway.enabled` ou `spring.flyway.*` n'est présent

### 🟡 VALIDATION DOCKER FIRST, BEFORE LOCAL TESTS
Le build Docker est la source de vérité. Si le build local passe mais que Docker échoue, c'est que :
1. Les dépendances Maven ont un scope incorrect dans le POM.xml
2. Le Dockerfile ignore des fichiers nécessaires
3. La config Spring bootstrape des beans qui ne sont pas testés localement

### 🔴 NO mixed-agent work per session
Chaque agent traite **un seul domaine** à la fois :
- Backend Agent → Java/Spring Boot + Dockerfile backend uniquement  
- Frontend Agent → Next.js/React uniquement
- Integration Agent → Connexion frontend ↔ backend uniquement
- NO cross-domain changes in the same commit/deploy cycle

### 🟡 AUTONOMOUS WORKFLOW - No blocking, only converging
Si un déploiement échoue sur Coolify :
1. **NE JAMAIS demander à l'utilisateur de copier-coller des logs** ← on a la CLI `~/.local/bin/coolify` installée
2. Récupérer automatiquement les logs du dernier déploiement via `coolify app logs <app-uuid>`
3. Comparer l'erreur locale vs Coolify, trouver la divergence, corriger localement avec test
4. Rebuild, valider, et relancer le deploy

### 🟡 PRIORITY ORDER (when multiple issues exist)
1. Build pass dans Docker (source de vérité pour Prod = Dev)
2. Code clean + compile local (rapidité de feedback)
3. Tests unitaires passent (qualité du code)
4. Features nouvelles (seulement quand 1-3 sont stables)

### 🟡 MONITORING - Auto-detect deployment failures
Après un `coolify app deploy <uuid>` :
- Attendre 60s puis faire `coolify app status <app-name>` pour vérifier l'état
- Si statut ≠ "running:healthy", automatiquement fetch les logs et diagnostiquer

### 🟡 NEVER IGNORE BUILD FAILURE
Tout échec de build DOIT être résolu AVANT toute autre tâche. C'est la priorité absolue du projet. 
Un projet qui ne construit pas est à l'arrêt, point barre.
