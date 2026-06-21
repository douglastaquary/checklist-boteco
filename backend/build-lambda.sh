#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./mvnw package -DskipTests -Dquarkus.profile=prod

if [[ ! -f target/function.zip ]]; then
  echo "Erro: target/function.zip não foi gerado. Verifique o build Quarkus Lambda." >&2
  exit 1
fi

echo "Artefato pronto: target/function.zip"
