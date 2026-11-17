#!/usr/bin/env python3
"""Best-effort DAD decompilation for review. Output is not original Kotlin or executable source."""
from pathlib import Path
from hashlib import sha256
from loguru import logger
from androguard.misc import AnalyzeAPK

logger.remove()
root = Path(__file__).resolve().parents[2]
apk_path = root / 'Stayar-v1_16.apk'
apk, dexes, analysis = AnalyzeAPK(str(apk_path))
classes = {
    'Lcom/workshop/ledger/web/NativeBridge;': 'legacy-bridge-dad.txt',
    'Lcom/workshop/ledger/web/Snd;': 'legacy-sound-dad.txt',
    'Lcom/workshop/ledger/data/StateValidator;': 'legacy-validator-dad.txt',
    'Lcom/workshop/ledger/data/LedgerRepository;': 'legacy-repository-dad.txt',
    'Lcom/workshop/ledger/data/SettingsRepository;': 'legacy-settings-dad.txt',
    'Lcom/workshop/ledger/data/LedgerRepository$replaceStore$2;': 'legacy-replace-coroutine-dad.txt',
    'Lcom/workshop/ledger/data/LedgerRepository$replaceStore$2$1;': 'legacy-replace-transaction-dad.txt',
}
apk_sha = sha256(apk_path.read_bytes()).hexdigest()
for name, filename in classes.items():
    cls = next(c for dex in dexes for c in dex.get_classes() if c.get_name() == name)
    lines = ['# Best-effort Androguard DAD pseudocode; NOT original Kotlin and may be inaccurate.',
             '# Verify all control flow and constants against original DEX instructions.',
             f'# APK SHA-256: {apk_sha}', f'# Class: {name}']
    failures = []
    for m in cls.get_methods():
        lines.append(f'\n## {m.get_name()} {m.get_descriptor()}')
        try:
            lines.append(m.get_source() or '[No body]')
        except Exception as exc:
            failures.append(m.get_name())
            lines.append(f'[DECOMPILATION FAILED: {type(exc).__name__}: {exc}]')
    target = root / 'docs' / filename
    target.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    print(f'{filename}: {len(cls.get_methods())} methods; {len(failures)} failures')
