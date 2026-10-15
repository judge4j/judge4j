#!/usr/bin/env bash
# Fails if the working tree is not safe to publish.
#
#   scripts/check-release-ready.sh      secrets + license checks
#
# Run it before the first commit to a public repository and before pushing a release tag.
# (The release version comes from the git tag, so the POM keeps its -SNAPSHOT version.)

set -u
cd "$(dirname "$0")/.."
status=0
fail() { echo "FAIL: $1"; status=1; }

# 1. Secrets: key-shaped default values and well-known key prefixes.
patterns=(
  '\$\{[A-Za-z0-9_.]+:[A-Za-z0-9_.-]{20,}\}'   # ${PROPERTY:long-literal-default}
  'AIza[0-9A-Za-z_-]{30,}'                      # Google API key
  'AQ\.[A-Za-z0-9_-]{30,}'                      # Google API key (newer format)
  'sk-[A-Za-z0-9_-]{20,}'                       # OpenAI / Anthropic style key
  'ghp_[A-Za-z0-9]{30,}'                        # GitHub token
  'AKIA[0-9A-Z]{16}'                            # AWS access key id
  '-----BEGIN [A-Z ]*PRIVATE KEY-----'
)
matches=$(grep -RInE "$(IFS='|'; echo "${patterns[*]}")" . \
  --exclude-dir=target --exclude-dir=.git --exclude-dir=.mvn --exclude-dir=node_modules \
  --exclude=check-release-ready.sh 2>/dev/null)
if [ -n "$matches" ]; then
  fail "possible secret(s) found:"
  echo "$matches" | sed -E 's/(.{0,80}).*/    \1.../'
  echo "    -> remove the literal (e.g. use \${GOOGLE_API_KEY:}) and rotate the exposed key."
fi

# 2. Legal files.
[ -s LICENSE ] || fail "LICENSE is missing"
[ -s NOTICE ] || fail "NOTICE is missing"

[ $status -eq 0 ] && echo "OK: ready to publish."
exit $status
