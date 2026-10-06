.PHONY: run build test docker-build docker-run down

build:
	cd backend && mvn -q -DskipTests package

test:
	cd backend && mvn test

run:
	cd backend && mvn spring-boot:run

docker-build:
	docker build -t solar-fleet-mvp:0.1.0 ./backend

docker-run:
	docker run --rm -p 8080:8080 \
	  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/solarfleet \
	  -e DB_USERNAME=solarfleet \
	  -e DB_PASSWORD=solarfleet \
	  solar-fleet-mvp:0.1.0

down:
	docker compose down
