#!/usr/bin/env bash

# shellcheck disable=SC1090
source "$(go run knative.dev/hack/cmd/script presubmit-tests.sh)"
source "$(go run knative.dev/hack/cmd/script infra-library.sh)"

function build_tests() {
  header "Running build tests"
  ./hack/build.sh || fail_test "build tests failed"
}

main $@
