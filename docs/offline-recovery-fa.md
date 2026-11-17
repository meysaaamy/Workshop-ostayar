# بکاپ و بازیابی آفلاین برای آزمایش مهاجرت

ابزار `parity/migration/encrypted_backup.py` یک ابزار میزبان Python است، **نه قابلیت بکاپ یا بازیابی داخل اپ Android**. SQLite را با API خود SQLite به یک پایگاه مستقل کپی می‌کند (شامل دادهٔ commitشده در WAL)، `quick_check` و نسخهٔ پایگاه را بررسی می‌کند و بایت‌های فایل DataStore را بدون تفسیر، تغییر نوع یا حذف کلیدهای ناشناخته کپی می‌کند. این شامل `auto_backup_envelope` و دیگر کلیدها هم می‌شود، اگر در همان فایل وجود داشته باشند. ابزار صحت protobuf، محتوای کلیدها، سازگاری آنها با DB، یا تبدیل به ساختار تنظیمات جدید را بررسی نمی‌کند. مکان فایل قدیمی استخراج‌شده از APK `files/datastore/ledger_settings.preferences_pb` است؛ انتقال معنایی DataStore و اتصال آن به برنامه **هنوز پیاده‌سازی و آزمایش نشده است**.

## پیش‌شرط مهم

پیش از بکاپ یا بازیابی، اپ و تمام writerهای SQLite و DataStore باید کاملاً متوقف باشند و در کل عملیات متوقف بمانند. تأیید `--app-stopped` فقط اقرار کاربر است و ابزار نمی‌تواند توقف دستگاه یا اتمی‌بودن بین SQLite و DataStore را تضمین کند. همواره روی نسخهٔ کپی کار کنید؛ دسترسی به مسیر خصوصی Android معمولاً به روش مجاز مثل محیط تست/نسخهٔ debug یا دسترسی خود دستگاه نیاز دارد. دادهٔ خصوصی را در مخزن Git یا پیام عمومی قرار ندهید.

```sh
python3 -m venv /secure/stayar-venv
/secure/stayar-venv/bin/pip install -r parity/migration/requirements-crypto.txt
# تمام مسیرها مثال هستند. مقصد نباید از قبل وجود داشته باشد.
/secure/stayar-venv/bin/python parity/migration/encrypted_backup.py backup \
  --database /secure/offline/workshop-ledger.db \
  --settings /secure/offline/ledger_settings.preferences_pb \
  --destination /secure/snapshot.enc --app-stopped
/secure/stayar-venv/bin/python parity/migration/encrypted_backup.py verify --snapshot /secure/snapshot.enc
/secure/stayar-venv/bin/python parity/migration/encrypted_backup.py restore \
  --snapshot /secure/snapshot.enc --destination /secure/restored --app-stopped
```

اگر با بررسی قطعی می‌دانید فایل تنظیمات وجود ندارد، به‌جای `--settings` صریحاً `--settings-absent` بدهید؛ نبود فایل در manifest ثبت می‌شود. خروجی بازیابی فایل‌های مستقل با همان نام‌ها در پوشهٔ جدید است و **خودکار در مسیر خصوصی اپ نصب یا جایگزین نمی‌شود**. هیچ فایل موجودی بازنویسی نمی‌شود. rollback اتمیک Room و DataStore روی دستگاه هنوز وجود ندارد. قطع برق و بازیابی بعد از آن روی دستگاه نیز آزموده نشده است.

فایل خروجی با AES-256-GCM و کلید مشتق‌شده از گذرواژه با scrypt رمزنگاری و احراز یکپارچگی می‌شود. گذرواژه حداقل ۱۲ نویسه است و تعاملی، بدون echo دریافت می‌شود؛ گذرواژهٔ طولانی و یکتا انتخاب کنید و جداگانه نگه دارید. از دست‌دادن آن به معنی ازدست‌دادن بکاپ است. رمزگذاری و بازیابی از فایل‌های موقت **متن‌واضح** در دایرکتوری خصوصی سیستم استفاده می‌کنند؛ برای TMPDIR از فضای امن و ترجیحاً رمزگذاری‌شده استفاده کنید. حذف فایل موقت پاک‌سازی امن از دیسک یا جلوگیری از swap و فایل‌های باقی‌مانده پس از قطع برق نیست. بازیابی نیز متن‌واضح تولید می‌کند. این ابزار مستقل درون اپ نیست؛ بکاپ رمزنگاری‌شده و restore درون‌برنامه‌ای هنوز مانع انتشارند. نسخه‌های بکاپ قدیمی plaintext خودبه‌خود رمزنگاری یا حذف نمی‌شوند؛ فرمان ساخت بکاپ plaintext قدیمی غیرفعال است، ولی verify/restore آن برای سازگاری باقی مانده است. برای رمزگذاری یک پوشهٔ بکاپ قبلی، `encrypted_backup.py encrypt-legacy --snapshot /secure/old-snapshot --destination /secure/old.enc` را اجرا کنید؛ پس از تأیید فایل جدید، دربارهٔ نگهداری امن نسخهٔ قدیمی تصمیم بگیرید.

## آزمون نسخهٔ قدیمی

یک تست میزبان، کلاس واقعی Kotlin برای ۲→۳ را روی نسخهٔ SQLite در حافظه اجرا و سپس rollback می‌کند. فقط DB نسخهٔ ۲ را قبول می‌کند، ابتدا `quick_check` می‌گیرد و digest تمام ردیف‌ها و ستون‌های اصلی را قبل و بعد مقایسه می‌کند. خروجی این تست، اثبات Room validation یا درست‌بودن محاسبات و تنظیمات نیست. روی **کپی آفلاین** اجرا شود؛ SQLite ممکن است هنگام بازکردن فایل WAL به فایل‌های جانبی نیاز داشته باشد. مسیر و داده‌های شخصی را عمومی نکنید.

```sh
JAVA_HOME=/path/to/jdk KOTLINC=/path/to/kotlinc \
  parity/tools/check-external-migration.sh /secure/restored/workshop-ledger.db
```

این ابزار در این نوبت فقط با fixture ساختگی حاصل از SQL استخراج‌شده از APK آزموده شد؛ دیتابیس واقعی ساخته‌شده توسط اپ قدیمی در workspace موجود نبود، بنابراین **آزمون واقعی نسخهٔ قدیمی هنوز انجام نشده است**. برای آزمون نهایی لازم است fixtureهای واقعی نسخه‌های ۱ و ۲ به‌شکل محرمانه تهیه شوند، و Android/Room همراه با DataStore، خرابی تزریقی و قطع فرآیند بررسی شوند. ۱→۲→۳ همچنان با دیتابیس واقعی نسخهٔ ۱ اثبات نشده است.

آزمون‌های خود ابزار:

```sh
/secure/stayar-venv/bin/python parity/migration/test_encrypted_backup.py
python3 parity/migration/test_offline_backup.py
python3 parity/migration/test_room_wiring.py
JAVA_HOME=/path/to/jdk KOTLINC=/path/to/kotlinc parity/tools/check-migration-sqlite.sh
```

ده تست بکاپ plaintext میزبان و نه تست رمزگذاری، WAL، حفظ دقیق بایت‌های تنظیمات، نبود صریح تنظیمات، جلوگیری از بازنویسی، تغییر همزمان فایل تنظیمات، خرابی، لینک نمادین، سقف اندازه و شکست‌های تزریق‌شدهٔ backup/restore را روی دادهٔ ساختگی بررسی می‌کنند. آزمون WAL با اتصال آزمون باز ولی بدون نویسندهٔ همزمان انجام شد و اثبات سازگاری بین دو منبع در حضور writer نیست.
