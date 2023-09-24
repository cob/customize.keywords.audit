#!/usr/bin/env bash
## Usage: ./run-tests.sh [-t be|fe]
## Summary: Run unit tests
##
## Options:
##    -t: run specific type of tests: be (backend), fe (frontend)
##    -h: print help information
##
## Examples:
##
##     * run all tests
##     ./run-tests.sh
##
##     * run backend tests
##     ./run-tests.sh -t be

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

customization="calc"
all="false"
type=""

while getopts "t:h" optname; do
  case "$optname" in
  "p")
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
  cat "$SCRIPT_DIR/run-tests.sh" | grep "^##.*" | sed "s/## //g" | sed "s/##//g"
  exit 1
fi

if [[ "$all" == "false" ]] && [[ "$type" == "" ]]; then
  echo "Running all tests"
  all="true"
fi

if [[ "$all" == "true" ]] || [[ "$type" == "be" ]]; then
  if [[ -f "integrationm/pom.xml" ]]; then
    (cd integrationm && mvn clean test)
  fi

  if [[ -f "others/validator-$customization/pom.xml" ]]; then
    (cd "others/validator-$customization" && mvn clean test)
  fi
fi
