#!/usr/bin/env bash
# Generate replication configs that use local project paths instead of GitHub URLs.
#
# The extended dataset configs reference GitHub URLs which triggers the pipeline
# to clone projects. For replication (where projects are pre-provided), we need
# configs that reference local paths.
#
# This script transforms:
#   root-path = "https://github.com/user/repo.git"
# Into:
#   root-path = "projects/github_com_user_repo"
#
# Usage:
#   ./replication/scripts/generate-replication-configs.sh
#
# Output:
#   project-configs/replication/extended/project-*.conf

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

SOURCE_DIR="$REPO_ROOT/project-configs/extended"
OUTPUT_DIR="$REPO_ROOT/project-configs/replication/extended"

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

# Sanitize GitHub URL to local project name
# Mirrors the logic in ProjectDownloadTask.java
sanitize_url() {
    local url="$1"

    # Remove protocol prefixes
    local cleaned="${url#https://}"
    cleaned="${cleaned#http://}"
    cleaned="${cleaned#git@}"

    # Replace ':' (from SSH URLs) with '/'
    cleaned="${cleaned//:/\/}"

    # Remove .git suffix
    cleaned="${cleaned%.git}"

    # Replace all non-alphanumeric (except underscore, hyphen) with '_'
    cleaned=$(echo "$cleaned" | sed 's/[^a-zA-Z0-9_-]/_/g')

    echo "$cleaned"
}

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Count configs
total=$(find "$SOURCE_DIR" -name "project-*.conf" | wc -l | tr -d ' ')
count=0

echo -e "${CYAN}Generating replication configs...${NC}"
echo "Source: $SOURCE_DIR"
echo "Output: $OUTPUT_DIR"
echo ""

# Process each config
for config in "$SOURCE_DIR"/project-*.conf; do
    [[ -f "$config" ]] || continue

    name=$(basename "$config")
    ((count++))

    # Extract the GitHub URL from the config
    url=$(grep 'root-path' "$config" | sed 's/.*= "//' | sed 's/".*//')

    if [[ -z "$url" ]]; then
        echo "Warning: No root-path found in $name, skipping"
        continue
    fi

    # Convert URL to local path
    project_name=$(sanitize_url "$url")
    local_path="projects/$project_name"

    # Generate new config with local path
    cat > "$OUTPUT_DIR/$name" << EOF
teralizer {
  project {
    root-path = "$local_path"
  }
}
EOF

    # Progress indicator every 100 configs
    if (( count % 100 == 0 )); then
        echo "  Processed $count / $total configs..."
    fi
done

echo ""
echo -e "${GREEN}Generated $count replication configs${NC}"
echo "Location: $OUTPUT_DIR"
