#!/bin/bash

SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Output should be empty if git isn't available; the output gets baked into the
# jar file and checked in the application code.
if ! command -v git &>/dev/null ; then
  exit
fi

if [ ! -d "$SCRIPTS_DIR/../.git" ]; then
  exit
fi

RELEASE_TAG=$("$SCRIPTS_DIR/newest-release-tag.sh")
if [ -z "$RELEASE_TAG" ]; then
  exit
fi

MERGE_BASE=$(git merge-base "$RELEASE_TAG" HEAD)
VERSION_TAG=$(git describe --contains --always "$MERGE_BASE" | sed 's/~.*//')

VERSION_STRING=$VERSION_TAG

DISTANCE=$(git rev-list --count "$MERGE_BASE"..HEAD)
VERSION_STRING+="-$DISTANCE"

SHA=$(git rev-parse --short HEAD)
VERSION_STRING+="-g$SHA"

DIRTY=$(git diff HEAD)
if [ -n "$DIRTY" ]; then
  VERSION_STRING+=-dirty
fi

echo $VERSION_STRING
