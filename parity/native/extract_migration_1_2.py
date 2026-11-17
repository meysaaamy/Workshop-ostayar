#!/usr/bin/env python3
"""Extract legacy 1→2 Room migration instructions and SQL from the verified APK."""
from pathlib import Path
from hashlib import sha256
import re
from androguard.core.apk import APK
from androguard.core.dex import DEX
from loguru import logger

logger.remove()
root = Path(__file__).resolve().parents[2]
source = root / 'Stayar-v1_16.apk'
apk = APK(str(source))
dex = DEX(apk.get_dex())
classes = {c.get_name(): c for c in dex.get_classes()}
owner = 'Lcom/workshop/ledger/data/LedgerDatabase;'
migration = 'Lcom/workshop/ledger/data/LedgerDatabase$Companion$MIGRATION_1_2$1;'
assert owner in classes and migration in classes
methods = [(owner, next(m for m in classes[owner].get_methods() if m.get_name() == '<clinit>'))]
methods += [(migration, m) for m in classes[migration].get_methods() if m.get_name() in ('<init>', 'migrate')]
assert len(methods) == 3
out = ['# Original DEX instructions; no Android runtime execution.', f'# APK sha256: {sha256(source.read_bytes()).hexdigest()}']
registers = {}
for cls, method in methods:
    out.append(f'\n## {cls}->{method.get_name()} {method.get_descriptor()}')
    for instruction in method.get_instructions():
        op, text = instruction.get_name(), instruction.get_output()
        out.append(f'{op} {text}'.rstrip())
        if cls == owner and method.get_name() == '<clinit>' and op == 'const-string':
            match = re.fullmatch(r'v(\d+), "(.*)"', text)
            if match and match.group(2).startswith('CREATE '):
                registers[int(match.group(1))] = match.group(2)
assert set(registers) == set(range(1,21)), 'Review DEX list construction before accepting changed input'
clinit = '\n'.join(out)
assert 'filled-new-array/range v1 ... v20' in clinit and 'MIGRATION_1_2_STATEMENTS' in clinit
assert '->execSQL(Ljava/lang/String;)V' in clinit
(root / 'docs/legacy-room-migration-1-2-dex.txt').write_text('\n'.join(out) + '\n', encoding='utf-8')
statements = [registers[i] for i in range(1,21)]
sql = ['-- Extracted from MIGRATION_1_2_STATEMENTS; not evidence of the actual version-1 schema.',
       f'-- APK sha256: {sha256(source.read_bytes()).hexdigest()}'] + [s + ';' for s in statements]
(root / 'docs/legacy-room-migration-1-2.sql').write_text('\n'.join(sql) + '\n', encoding='utf-8')
print('Extracted 20 ordered SQL statements and migration DEX; no migration performed.')
