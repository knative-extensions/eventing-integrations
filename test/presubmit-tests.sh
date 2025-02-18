#!/usr/bin/env bash

# shellcheck disable=SC1090
source "$(go run knative.dev/hack/cmd/script presubmit-tests.sh)"

function build_tests() {
  header "Running build tests"
  export IS_PROW
  ./hack/build.sh || fail_test "build tests failed"
}

main $@
