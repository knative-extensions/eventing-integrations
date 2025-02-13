#!/usr/bin/env bash

set -euo pipefail

export TAG=${TAG:-$(git rev-parse HEAD)}

[[ ! -v REPO_ROOT_DIR ]] && REPO_ROOT_DIR="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT_DIR

function build_transform_jsonata_image() {
  local image="${KO_DOCKER_REPO}/transform-jsonata:${TAG}"

  docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t "${image}" \
    -f "${REPO_ROOT_DIR}/transform-jsonata/Dockerfile" \
    --push \
    "${REPO_ROOT_DIR}/transform-jsonata" || return $?


  TRANSFORM_JSONATA_IMAGE=$(docker inspect --format '{{index .RepoDigests 0}}' "${image}")
  export TRANSFORM_JSONATA_IMAGE
}
