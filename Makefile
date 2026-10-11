.PHONY: run build test docker-build docker-up docker-down health wrapper-check

build:
	cd backend && ./mvnw -q -DskipTests package

test:
	cd backend && ./mvnw test

run:
	cd backend && ./mvnw spring-boot:run

docker-build:
	docker compose build

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down

health:
	@curl -fsS http://localhost:8080/actuator/health
	@echo
	@curl -fsS http://localhost:8080/api/v1/dashboard/summary | head -c 200
	@echo

wrapper-check:
	cd backend && ./mvnw -v
