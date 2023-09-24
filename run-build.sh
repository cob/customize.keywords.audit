#!/usr/bin/env bash
## Usage: ./run-build.sh [-t be|fe]
## Summary: Run unit tests
##
## Options:
##    -t: run specific type of tests: be (backend), fe (frontend)
##    -h: print help information
##
## Examples:
##
##     * build all
##     ./run-build.sh
##
##     * build backend
##     ./run-build.sh -t be

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

customization="calc"
all="false"
type=""

while getopts "t:h" optname; do
  case "$optname" in
  "t")
    type="$OPTARG"
    ;;
  "h")
    print_help="true"
    ;;
  "?")
    echo "Unknown option $OPTARG"
    ;;
  ":")
    echo "No argument value for option $OPTARG"
    ;;
  *)
    # Should not occur
    echo "Unknown error while processing options"
    ;;
  esac
done

if [[ "$print_help" == "true" ]]; then
  cat "$SCRIPT_DIR/run-build.sh" | grep "^##.*" | sed "s/## //g" | sed "s/##//g"
  exit 1
fi

if [[ "$all" == "false" ]] && [[ "$type" == "" ]]; then
  echo "Building all"
  all="true"
fi

if [[ "$all" == "true" ]] || [[ "$type" == "be" ]]; then
  if [[ -f "others/validator-$customization/pom.xml" ]]; then
      (cd "others/validator-$customization" && ./build_and_copy.sh)
    fi
fi
