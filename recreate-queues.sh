#!/bin/bash

# Stop and remove the specific container, including its volumes and networks
docker compose down --volumes --remove-orphans

# Start the container again
docker compose up -d
