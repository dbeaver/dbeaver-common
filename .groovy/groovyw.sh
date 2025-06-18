#!/usr/bin/env sh

set -e

script_dir="$(realpath "$(dirname "$0")")"

get_property() {
    grep "$1" "$script_dir/properties.toml" | awk -F '=' '{print $2}' | tr -d ' "'
}

groovy_version=$(get_property 'groovy.version')
groovy_dir="$script_dir/groovy-$groovy_version"

if [ ! -d "$groovy_dir" ]; then
    echo "Downloading Groovy $groovy_version..."
    download_url="$(get_property 'groovy.downloadUrlPrefix')-$groovy_version.zip"
    curl -L "$download_url" -o "$script_dir/groovy.zip"
    echo "Unpacking Groovy $groovy_version..."
    unzip -q "$script_dir/groovy.zip" -d "$script_dir"
    rm "$script_dir/groovy.zip"
fi

"$groovy_dir/bin/groovy" "$@"
