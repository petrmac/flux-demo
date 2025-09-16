#!/bin/bash

echo "🔌 Port Forwarding Script"
echo "========================="

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "\n${YELLOW}Starting port forwards...${NC}"

# Function to handle port forwarding with error handling
forward_port() {
    local namespace=$1
    local resource=$2
    local port=$3
    local name=$4

    echo -e "\n${GREEN}$name${NC}"
    echo "URL: http://localhost:$port"
    kubectl port-forward -n $namespace $resource $port &
}

# Demo Service
forward_port "demo-service" "svc/demo-service" "8080:8080" "Demo Service API"

# OpenTelemetry Collector metrics
forward_port "opentelemetry" "svc/otel-collector" "8889:8889" "OpenTelemetry Metrics"

# OpenTelemetry Collector zPages
forward_port "opentelemetry" "deployment/otel-collector" "55679:55679" "OpenTelemetry zPages"

echo -e "\n${GREEN}Port forwards started!${NC}"
echo -e "\nAvailable endpoints:"
echo -e "  ${YELLOW}Demo Service:${NC}"
echo -e "    - Health: http://localhost:8080/api/health"
echo -e "    - Info: http://localhost:8080/api/info"
echo -e "    - Greeting: http://localhost:8080/api/greeting/World"
echo -e "    - Metrics: http://localhost:8080/actuator/prometheus"
echo -e "\n  ${YELLOW}OpenTelemetry:${NC}"
echo -e "    - Collector Metrics: http://localhost:8889/metrics"
echo -e "    - zPages: http://localhost:55679"
echo -e "\n${YELLOW}Press Ctrl+C to stop all port forwards${NC}"

# Wait for user to stop
wait