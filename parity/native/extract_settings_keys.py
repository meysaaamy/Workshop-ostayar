#!/usr/bin/env python3
"""Record APK DEX instructions supporting settings names and key mapping."""
from hashlib import sha256
from pathlib import Path
from androguard.core.apk import APK
from androguard.core.dex import DEX
from loguru import logger
logger.remove()
root=Path(__file__).resolve().parents[2]
apk=root/'Stayar-v1_16.apk';dex=DEX(APK(str(apk)).get_dex())
classes={c.get_name():c for c in dex.get_classes()}
name='Lcom/workshop/ledger/data/LedgerPreferenceKeys;'
lines=['# Raw DEX instructions; static evidence, not proof of runtime DataStore behavior.',f'# APK SHA-256: {sha256(apk.read_bytes()).hexdigest()}']
for method_name in ('<clinit>','projectIdFromKeyName','projectSettings'):
    method=next(m for m in classes[name].get_methods() if m.get_name()==method_name)
    lines.append(f'\n## {name}->{method_name} {method.get_descriptor()}')
    lines += [f'{i.get_name()} {i.get_output()}'.rstrip() for i in method.get_instructions()]
body='\n'.join(lines)+'\n'
for text in ('active_project_id','attendance_coach_dismissed','auto_backup_envelope','onboarding_decision','project_','_settings'):
    assert text in body
(root/'docs/legacy-settings-keys-dex.txt').write_text(body,encoding='utf-8')
print('Extracted legacy preference key instructions from APK.')
