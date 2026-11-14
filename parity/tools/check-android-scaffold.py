#!/usr/bin/env python3
"""Static checks of the unfinished scaffold. Does not build or inspect a merged manifest."""
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[2]
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'Unexpected explicit permission'
app = manifest.find('application')
assert app.get(ns + 'allowBackup') == 'false'
assert app.get(ns + 'supportsRtl') == 'true'
activity = app.find('activity')
assert activity.get(ns + 'exported') == 'true'
config = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
for pattern in [r'applicationId\s*=\s*"com\.workshop\.ledger"', r'minSdk\s*=\s*26',
                r'targetSdk\s*=\s*35', r'versionCode\s*=\s*2', r'applicationIdSuffix\s*=\s*"\.dev"']:
    assert re.search(pattern, config), pattern
props = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
assert 'distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab' in props
assert hashlib.sha256((root / 'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest() == '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'
print('Static scaffold checks passed; Gradle build, merged manifest, signing and Android runtime not tested.')
