#!/usr/bin/env sh

set -e

script_dir="$(realpath "$(dirname "$0")")"

groovy_version="4.0.27"
groovy_dir="$script_dir/groovy-$groovy_version"
groovy_download_url="https://groovy.jfrog.io/artifactory/dist-release-local/groovy-zips/apache-groovy-binary-$groovy_version.zip"

if [ ! -d "$groovy_dir" ]; then
    echo "Downloading Groovy $groovy_version..."
    curl -L "$groovy_download_url" -o "$script_dir/groovy.zip"
    echo "Unpacking Groovy $groovy_version..."
    unzip -q "$script_dir/groovy.zip" -d "$script_dir"
    rm "$script_dir/groovy.zip"
fi

"$script_dir/groovy-$groovy_version/bin/groovy" "$@"
