#!/usr/bin/env bash

# Copyright 2024 The Knative Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# shellcheck disable=SC1090
source "$(go run knative.dev/hack/cmd/script release.sh)"

function build_release() {
  header "Building images"

  # KO_DOCKER_REPO and TAG are calculated in release.sh script

  ./mvnw clean package -P knative -DskipTests || return $?

  echo "Image build & push completed"
}

main "$@"
