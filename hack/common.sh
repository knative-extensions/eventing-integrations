#!/usr/bin/env bash

set -euo pipefail

export TAG=${TAG:-$(git rev-parse HEAD)}

export KO_DOCKER_REPO="${KO_DOCKER_REPO:-kind.local}"
export TRANSFORM_JSONATA_IMAGE_WITH_TAG="${KO_DOCKER_REPO}/transform-jsonata:${TAG}"

[[ ! -v REPO_ROOT_DIR ]] && REPO_ROOT_DIR="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT_DIR

export TRANSFORM_JSONATA_DIR="${REPO_ROOT_DIR}/transform-jsonata"

function build_transform_jsonata_image() {

  download_pack_cli || return $?

  cd "${TRANSFORM_JSONATA_DIR}" && \
    pack build "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-arm64" \
      --builder docker.io/heroku/builder:24 \
      --platform "linux/arm64" \
      --clear-cache && \
    cd -

  cd "${TRANSFORM_JSONATA_DIR}" && \
    pack build "${TRANSFORM_JSONATA_IMAGE_WITH_TAG}-amd64" \
      --builder docker.io/heroku/builder:24 \
      --platform "linux/amd64" \
      --clear-cache && \
    cd -
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
    TRANSFORM_JSONATA_IMAGE="${TRANSFORM_JSONATA_IMAGE_WITH_TAG}@${digest}"
    export TRANSFORM_JSONATA_IMAGE

    echo "TRANSFORM_JSONATA_IMAGE=${TRANSFORM_JSONATA_IMAGE}"
}

function build_integration_images() {
  "${REPO_ROOT_DIR}/mvnw" clean package -P knative -DskipTests || return $?
}

function download_pack_cli() {
  local dir
  dir="$(mktemp -d)"
  git clone --depth 1 --branch "v0.36.4" https://github.com/buildpacks/pack.git "${dir}"
  cd "${dir}" && go install ./cmd/pack && cd - || return $?
  rm -rf "${dir}"
}
