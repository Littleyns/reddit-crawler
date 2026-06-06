# Pipeline Autonome Standard — ArabTooling

## Principe
- **Pas de cron** toutes les X min (trop de temps mort pour inference GPU)
- **Pipeline bouclé à 60s** : delegate_task(batch: workers OpenCode) → vérification Docker → relance
- Chaque tick = **3 subagents en parallèle** = 3x inference active simultanée
- GPU distant `192.168.100.1:11434` toujours sollicité par au moins un worker

## Workers standards (configurés par projet)

### Pool de workers
```yaml
pool:
  - name: backend-worker
    workdir: /home/kali/projects/<project>/backend-java/
    toolsets: [terminal, file]
    
  - name: frontend-worker
    workdir: /home/kali/projects/<project>/frontend/
    toolsets: [terminal, file]
    
  - name: infra-worker
    workdir: /home/kali/projects/<project>/
    toolsets: [terminal]
```

### Rotation des tâches (par tick)
```yaml
task_rotation:
  - "backend: Implement endpoint X feature Y"
  - "frontend: Fix component Z, add hook W"
  - "infra: Docker build & deploy, verify health"
```

## Commande de lancement (standard pour tous projets)

```bash
cd /home/kali/projects/<project>
bash pipeline.sh
```

Le script `pipeline.sh` contient :
1. Boucle while(true) à 60s
2. `delegate_task(tasks=[...])` avec le pool de workers
3. Chaque worker lance `open-code run '<tâche spécifique>' --model qwen3.6:35b` sur son workdir
4. Build Docker auto après chaque cycle

## Tâches standards par projet (à adapter)

### Backend Spring Boot
- Ajouter endpoints REST avec Security filter chain correcte
- Optimiser JPA queries + H2/PostgreSQL dialects
- Implement tests unitaires + integration tests
- Améliorer logging + monitoring health endpoints

### Frontend Next.js
- Fix des hooks (useAnalytics, useCrawlerStatus, etc.)
- Implémenter pages manquantes du dashboard
- Optimiser API calls polling → fallback mock data si backend down
- Components réutilisables + validation Zod forms

### Infrastructure Docker
- Build images propre (no-cache)
- docker compose up --profile all -d
- Verify health via /actuator/health + UI accessible
- Push vers Coolifysi ready (tags semver)

## Pipeline.sh template

Voir infra/pipeline.sh (généré automatiquement au 1er tick)
