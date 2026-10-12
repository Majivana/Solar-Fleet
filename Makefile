.PHONY: help build test verify run db up up-prod down logs clean docker-build
MVN := ./mvnw

help:            ## list targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | sed 's/:.*##/ -/'

build:           ## compile and package (skips tests)
	$(MVN) -q -DskipTests package

test:            ## run unit tests (set IT_DB_URL to include the PostgreSQL migration test)
	$(MVN) test

verify:          ## full build: tests + coverage report
	$(MVN) verify

db:              ## start only PostgreSQL on 127.0.0.1:5432 for local development
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db

run: db          ## run the app locally in demo mode against the dev database
	SPRING_PROFILES_ACTIVE=demo $(MVN) -pl backend spring-boot:run

up:              ## full demo stack (proxy + app + db) at http://localhost:8080
	docker compose up --build -d

up-prod:         ## production-style stack; needs a filled-in .env
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d

down:            ## stop the stack
	docker compose down

logs:            ## follow application logs
	docker compose logs -f app

docker-build:    ## build the application image only
	docker build -t solar-fleet-mvp:0.1.0 ./backend

clean:           ## remove build output
	$(MVN) -q clean
