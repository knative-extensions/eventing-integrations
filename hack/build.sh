#!/usr/bin/env bash

source "$(dirname $0)/common.sh"

export KO_DOCKER_REPO="${KO_DOCKER_REPO:-kind.local}"

build_transform_jsonata_image || exit $?
push_transform_jsonata_image || exit $?

#build_integration_images || exit $?
