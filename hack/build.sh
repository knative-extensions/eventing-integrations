#!/usr/bin/env bash

source "$(dirname $0)/common.sh"

err_report() {
    echo "!!! Error on line $1 !!!"
    exit 1
}

set -T          # inherit DEBUG and RETURN trap for functions
set -C          # prevent file overwrite by > &> <>
set -E          # inherit -e
set -e          # exit immediately on errors
set -u          # exit on not assigned variables
set -o pipefail # exit on pipe failure

trap 'err_report $LINENO' ERR

export KO_DOCKER_REPO="${KO_DOCKER_REPO:-kind.local}"

build_transform_jsonata_image || exit $?

build_integration_images || exit $?
