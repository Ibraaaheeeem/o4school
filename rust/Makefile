.PHONY: help build run check test clean fmt lint docker-up docker-down

help:
	@echo "School Management Backend - Available Commands"
	@echo ""
	@echo "Development:"
	@echo "  make build          - Build the project"
	@echo "  make run            - Run the development server"
	@echo "  make check          - Check for compilation issues"
	@echo "  make test           - Run tests"
	@echo "  make fmt            - Format code"
	@echo "  make lint           - Run clippy linter"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-up      - Start services with Docker Compose"
	@echo "  make docker-down    - Stop Docker Compose services"
	@echo "  make docker-build   - Build Docker image"
	@echo ""
	@echo "Utilities:"
	@echo "  make clean          - Remove build artifacts"
	@echo "  make docs           - Generate documentation"
	@echo ""

build:
	cargo build

release:
	cargo build --release

run:
	cargo run

check:
	cargo check

test:
	cargo test -- --nocapture

fmt:
	cargo fmt

lint:
	cargo clippy -- -D warnings

clean:
	cargo clean

docs:
	cargo doc --open

docker-build:
	docker build -t school-backend .

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

docker-logs:
	docker-compose logs -f

db-create:
	createdb school_management

db-drop:
	dropdb school_management

install-tools:
	cargo install cargo-watch
	cargo install cargo-tarpaulin

watch:
	cargo watch -x check -x test

coverage:
	cargo tarpaulin --out Html

all: check fmt lint test build
	@echo "All checks passed!"
