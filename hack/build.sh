#!/usr/bin/env bash

source "$(dirname $0)/common.sh"

export KO_DOCKER_REPO="${KO_DOCKER_REPO:-kind.local}"

build_transform_jsonata_image || exit 1

build_integration_images || exit 1
