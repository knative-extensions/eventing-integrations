#!/usr/bin/env bash

set -euo pipefail

export TAG=${TAG:-$(git rev-parse HEAD)}

export TRANSFORM_JSONATA_IMAGE_WITH_TAG="${KO_DOCKER_REPO}/transform-jsonata:${TAG}"

[[ ! -v REPO_ROOT_DIR ]] && REPO_ROOT_DIR="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT_DIR

function build_transform_jsonata_image() {

  docker version

  docker buildx build \
    --platform "linux/amd64" \
    -t "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" \
    -f "${REPO_ROOT_DIR}/transform-jsonata/Dockerfile" \
    --output=type=docker \
    "${REPO_ROOT_DIR}/transform-jsonata" || return $?

  docker buildx build \
    --platform "linux/arm64" \
    -t "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" \
    -f "${REPO_ROOT_DIR}/transform-jsonata/Dockerfile" \
    --output=type=docker \
    "${REPO_ROOT_DIR}/transform-jsonata" || return $?
}

function push_transform_jsonata_image() {
    docker push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" || return $?
    docker push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" || return $?

    echo "Creating manifest ${TRANSFORM_JSONATA_IMAGE_WITH_TAG}"
    docker manifest create "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}" \
        "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" \
        "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" || return $?

    echo "Pushing manifest ${TRANSFORM_JSONATA_IMAGE_WITH_TAG}"
    docker manifest push "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}" || return $?
    # TODO figure out digest properly
#    TRANSFORM_JSONATA_IMAGE=$(docker inspect --format '{{index .RepoDigests 0}}' "${image}")
#    export TRANSFORM_JSONATA_IMAGE
}

function build_integration_images() {
  "${REPO_ROOT_DIR}/mvnw" clean package -P knative -DskipTests || return $?
}
