#!/usr/bin/env bash
# Type-check the app and run its pure unit tests without an Android SDK. Prints errors + a test summary.
cd "$(dirname "$0")"
/opt/gradle/bin/gradle test --no-daemon -q --max-workers 2 2>&1 | grep -v JAVA_TOOL_OPTIONS | grep -vE "^\s+at " | grep -E "^e:|^w: .*(deprecated|unresolved)|What went wrong|> Task .* FAILED|tests completed|Test.*FAILED|Exception|expected|but was" -A2 | head -${LINES_MAX:-80}
code=${PIPESTATUS[0]}
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=0
for p in glob.glob('build/test-results/test/*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors'))
print(f'TESTS {t} FAILED {f}')
PY
echo "GRADLE_EXIT $code"
