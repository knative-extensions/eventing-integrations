#!/usr/bin/env bash

set -euo pipefail

export JSONATA_IMAGE_TAG=${TAG:-$(git rev-parse HEAD)}

export TRANSFORM_JSONATA_IMAGE_REPO="${KO_DOCKER_REPO:-kind.local}/transform-jsonata"
export TRANSFORM_JSONATA_IMAGE_WITH_TAG="${TRANSFORM_JSONATA_IMAGE_REPO}:${JSONATA_IMAGE_TAG}"

[[ ! -v REPO_ROOT_DIR ]] && REPO_ROOT_DIR="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT_DIR

export TRANSFORM_JSONATA_DIR="${REPO_ROOT_DIR}/transform-jsonata"


function build_transform_jsonata_image() {

  if (( ${IS_PROW:-0} )); then
    docker run --privileged --rm tonistiigi/binfmt --install all binfmt
  fi

  docker info
  docker buildx ls

  echo "Building image for arm64"
  docker buildx build \
    --debug \
    --pull \
    -t "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" \
    -f "${REPO_ROOT_DIR}/transform-jsonata/Dockerfile" \
    --platform "linux/arm64" \
    "${REPO_ROOT_DIR}/transform-jsonata" \
    || return $?

  echo "Building image for amd64"
  docker buildx build \
    --debug \
    --pull \
    -t "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" \
    -f "${REPO_ROOT_DIR}/transform-jsonata/Dockerfile" \
    --platform "linux/amd64" \
    "${REPO_ROOT_DIR}/transform-jsonata" \
    || return $?
}

function push_transform_jsonata_image() {
    docker push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" || return $?
    docker push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" || return $?

    echo "Creating manifest ${TRANSFORM_JSONATA_IMAGE_WITH_TAG}"
    docker manifest create --amend "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}" \
        "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" \
        "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" || return $?

    echo "Pushing manifest ${TRANSFORM_JSONATA_IMAGE_WITH_TAG}"
    docker manifest push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}" || return $?

    digest=$(docker buildx imagetools inspect "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}" --format "{{json .Manifest.Digest }}" | tr -d '"')
    TRANSFORM_JSONATA_IMAGE="${TRANSFORM_JSONATA_IMAGE_REPO}@${digest}"
    export TRANSFORM_JSONATA_IMAGE

    echo "TRANSFORM_JSONATA_IMAGE=${TRANSFORM_JSONATA_IMAGE}"
}

function build_integration_images() {
  "${REPO_ROOT_DIR}/mvnw" clean package -P knative -DskipTests || return $?
}
