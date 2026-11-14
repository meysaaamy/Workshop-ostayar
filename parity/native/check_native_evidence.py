#!/usr/bin/env python3
"""Check a small, explicit set of claims against APK DEX, not decompiled pseudocode."""
from pathlib import Path
from hashlib import sha256
import json
from loguru import logger
from androguard.core.apk import APK
from androguard.core.dex import DEX

logger.remove()
root = Path(__file__).resolve().parents[2]
file = root / 'Stayar-v1_16.apk'
apk = APK(str(file))
dex = DEX(apk.get_dex())

def method(cls, name):
    c = next(c for c in dex.get_classes() if c.get_name() == cls)
    m = next(m for m in c.get_methods() if m.get_name() == name)
    return [f'{i.get_name()} {i.get_output()}' for i in m.get_instructions()]

def contains(lines, text):
    assert any(text in line for line in lines), f'Missing DEX evidence: {text}'

bridge = 'Lcom/workshop/ledger/web/NativeBridge;'
for name in ('saveState', 'validateState', 'saveAutoBackup'):
    body = method(bridge, name)
    contains(body, 'Ljava/lang/String;->length()I')
    contains(body, '83886080')
contains(method(bridge, 'validateState'), 'StateValidator;->validate$default')
contains(method(bridge, 'saveState'), 'LedgerRepository;->replaceStoreBlocking')
backup = method(bridge, 'saveAutoBackup')
contains(backup, '"snaps"')
contains(backup, 'const/4 v4, 3')
validator = 'Lcom/workshop/ledger/data/StateValidator;'
contains(method(validator, 'validate$default'), '67108864')
contains(method(validator, 'validate'), '->utf8Length(')
payouts = method(validator, 'validatePayouts')
contains(payouts, 'getUserIds()')
contains(payouts, 'Issues;->warn(')
assert not any('getType()' in line for line in payouts), 'Payout validator now appears to check user type; review manually'
repo = method('Lcom/workshop/ledger/data/LedgerRepository$replaceStore$2;', 'invokeSuspend')
a = next(i for i, line in enumerate(repo) if 'RoomDatabaseKt;->withTransaction' in line)
b = next(i for i, line in enumerate(repo) if 'SettingsRepository;->writeSnapshot' in line)
assert a < b, 'Expected Room transaction before DataStore snapshot call'
sound = method('Lcom/workshop/ledger/web/Snd;', 'play')
for name in ('->setEncoding(I)', '->setSampleRate(I)', '->setChannelMask(I)'):
    contains(sound, name)
html = apk.get_file('assets/index.html')
assert b'const PCM_RATE = 22050' in html
assert b'new Int16Array(N)' in html
assert b'NB.playPcm(_synth(notes), PCM_RATE)' in html
findings = {
    'apkSha256': sha256(file.read_bytes()).hexdigest(),
    'package': apk.package,
    'bridgeStringLengthLimitCodeUnits': 83886080,
    'stateValidatorDefaultUtf8ByteLimit': 67108864,
    'backupSnapLimit': 3,
    'payoutValidatorChecksUserExistenceButNoTypeCallSeen': True,
    'replacementCallOrder': ['RoomDatabaseKt.withTransaction', 'SettingsRepository.writeSnapshot'],
    'pcmSynthRateHz': 22050,
    'pcmSynthArray': 'Int16Array',
    'note': 'Static code evidence only. Control flow, persistence failures and media output require device tests.'
}
out = root / 'docs' / 'legacy-native-evidence.json'
out.write_text(json.dumps(findings, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('Verified selected original DEX instructions, limits, call order and JavaScript PCM constants.')
