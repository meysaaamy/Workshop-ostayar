#!/usr/bin/env python3
"""Extract selected native-validator disassembly; evidence, not reconstructed Kotlin source."""
from pathlib import Path
from hashlib import sha256
from loguru import logger
from androguard.core.apk import APK
from androguard.core.dex import DEX

logger.remove()
root = Path(__file__).resolve().parents[2]
apk_path = root / 'Stayar-v1_16.apk'
apk = APK(str(apk_path))
dex = DEX(apk.get_dex())
name = 'Lcom/workshop/ledger/data/StateValidator;'
cls = next(c for c in dex.get_classes() if c.get_name() == name)
names = {'<clinit>', 'isJalaliDate', 'isValidAmount', 'isValidAttendanceValue', 'checkAmount', 'checkDate', 'checkRecordId'}
lines = ['# Selected original DEX instructions (not decompiled source)',
         f'# APK sha256: {sha256(apk_path.read_bytes()).hexdigest()}',
         f'# package: {apk.package}; versionCode: {apk.get_androidversion_code()}']
found = set()
for m in cls.get_methods():
    if m.get_name() not in names:
        continue
    found.add(m.get_name())
    lines.append(f'\n## {name}->{m.get_name()} {m.get_descriptor()}')
    for instruction in m.get_instructions():
        lines.append(f'{instruction.get_name()} {instruction.get_output()}')
assert found == names, f'missing methods: {names - found}'
out = root / 'docs' / 'legacy-validator-dex.txt'
out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'Wrote {len(found)} original DEX methods to {out.relative_to(root)}')

# Preserve full instructions for both native classes as an auditable reference.
for class_name, filename in [
    ('Lcom/workshop/ledger/data/StateValidator;', 'legacy-validator-all-dex.txt'),
    ('Lcom/workshop/ledger/web/NativeBridge;', 'legacy-bridge-dex.txt'),
    ('Lcom/workshop/ledger/data/LedgerRepository$replaceStore$2;', 'legacy-replace-coroutine-dex.txt'),
    ('Lcom/workshop/ledger/data/LedgerRepository$replaceStore$2$1;', 'legacy-replace-transaction-dex.txt'),
]:
    item = next(c for c in dex.get_classes() if c.get_name() == class_name)
    raw = ['# Original DEX instructions; control flow has not been decompiled.',
           f'# APK sha256: {sha256(apk_path.read_bytes()).hexdigest()}']
    for method in item.get_methods():
        raw.append(f'\n## {class_name}->{method.get_name()} {method.get_descriptor()}')
        raw.extend(f'{instruction.get_name()} {instruction.get_output()}' for instruction in method.get_instructions())
    output = root / 'docs' / filename
    output.write_text('\n'.join(raw) + '\n', encoding='utf-8')
    print(f'Wrote {len(item.get_methods())} methods to {output.relative_to(root)}')
