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

CONFIMAP_YAML="eventing-integrations-images.yaml"
IMAGES_TXT="images.txt"

function build_release() {
  header "Building images"

  # KO_DOCKER_REPO and TAG are calculated in release.sh script

  ./mvnw clean package -P knative -DskipTests || return $?

  echo "Image build & push completed"

  echo "Collect image shas into ConfigMap"
  generate_configMap

  ARTIFACTS_TO_PUBLISH="${CONFIMAP_YAML} ${IMAGES_TXT}"
  sha256sum ${ARTIFACTS_TO_PUBLISH} >checksums.txt
  ARTIFACTS_TO_PUBLISH="${ARTIFACTS_TO_PUBLISH} checksums.txt"
  echo "Checksum:"
  cat checksums.txt
}

function generate_configMap() {
  image_digests=$(find . -name "jib-image.digest")

  cat <<EOF > ${CONFIMAP_YAML}
    apiVersion: v1
    kind: ConfigMap
    metadata:
      name: eventing-integrations-images
    data:
EOF

  # shellcheck disable=SC2068
  for image_digest_path in ${image_digests[@]}; do
    echo "Processing image: ${image_digest_path}"
    image=$(echo "${image_digest_path}" | cut -d "/" -f2)
    image_digest=$(cat "${image_digest_path}")
    # Config map with key value imageName:imageRef
    echo "  ${image}: ${KO_DOCKER_REPO}/${image}:${image_digest}" >>${CONFIMAP_YAML}
    # Storing plain image URLs in a txt file
    echo "${KO_DOCKER_REPO}/${image}:${image_digest}" >>${IMAGES_TXT}
  done
}

main "$@"
