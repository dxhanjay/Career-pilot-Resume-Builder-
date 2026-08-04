# docker/

## What's here

`docker-compose.yml` — PostgreSQL 16 for local development. The application
itself runs from your IDE or `./mvnw spring-boot:run`, because a hot-reload loop
beats rebuilding an image on every change.

```bash
docker compose -f docker/docker-compose.yml up -d
```

## Where the Dockerfile went

It now lives at **[`backend/Dockerfile`](../backend/Dockerfile)**.

It moved during Phase 4 because of a build-context constraint, not a preference.
Railway's *Root Directory* for the backend service is `backend/`, and the Docker
build context follows that setting. A Dockerfile written for the repository root
starts with `COPY backend/pom.xml`, and from inside a `backend/` context there is
no `backend/` directory — so the build fails with a "file not found" that points
at the copy line rather than at the context.

Keeping a second, root-context copy here would mean two Dockerfiles that must be
edited together and will eventually disagree. One Dockerfile, in the directory
its context matches:

```bash
docker build -t careerpilot-api backend/
```

That is the same command Railway effectively runs, so a local build failure is a
deploy failure caught early.
