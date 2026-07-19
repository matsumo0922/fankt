#!/usr/bin/env bash

set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "$script_directory/.." && pwd)"
consumer_project="$repository_root/compatibility-tests/ktor-independent-consumer"
consumer_repository="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fankt-consumer-repository.XXXXXX")"

cleanup() {
    rm -rf -- "$consumer_repository"
}
trap cleanup EXIT

"$repository_root/gradlew" \
    -p "$repository_root" \
    :fankt:fanbox:verifyKtorBoundary \
    :fankt:fanbox:publishKotlinMultiplatformPublicationToMavenLocal \
    :fankt:fanbox:publishAndroidReleasePublicationToMavenLocal \
    -Dmaven.repo.local="$consumer_repository" \
    --no-configuration-cache

verify_consumer() {
    "$repository_root/gradlew" \
        -p "$consumer_project" \
        verifyKtorSelection \
        -PfanktRepository="$consumer_repository" \
        --configuration-cache \
        --configuration-cache-problems=fail
}

verify_consumer
verify_consumer
