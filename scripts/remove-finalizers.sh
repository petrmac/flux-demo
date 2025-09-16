#!/bin/bash
echo "⚡️ Script to remove finalizers from kubernetes namespace if something gets stuck"

set -eou pipefail

if [ "$#" -eq 0 ]; then
    echo "This script requires at least one namespace as parameter. None found. Exiting."
    exit 1
fi

for namespace in "$@"
do
  kubectl get namespace "$namespace" -o json | jq '.spec = {"finalizers":[]}' > rknf_tmp.json
  kubectl proxy &
  sleep 5
  curl -H "Content-Type: application/json" -X PUT --data-binary @rknf_tmp.json http://localhost:8001/api/v1/namespaces/$namespace/finalize
  pkill -9 -f "kubectl proxy"
  rm rknf_tmp.json
done

