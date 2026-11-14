# اسکلت اندروید — وضعیت آزمایشی

ساختار `:domain` (Kotlin/JVM)، `:data` (کتابخانهٔ Android) و `:app` (Android/Compose) و wrapper گرادل اضافه شده است. صفحهٔ فعلی فقط هشدار فارسی راست‌به‌چپ دارد؛ پروژه‌ها، ذخیره‌سازی عملیاتی، مهاجرت، گزارش و آموزش هنوز متصل یا پیاده‌سازی نشده‌اند. یک Room آزمایشی جدا برای دفتر ساخته شده اما برنامه آن را باز نمی‌کند و هنوز روی Android آزموده نشده است. شناسهٔ release برابر `com.workshop.ledger`، `versionCode=2`، حداقل SDK 26 و هدف 35 است؛ `allowBackup=false` و در manifest منبع هیچ permission شبکه‌ای وجود ندارد. شناسهٔ debug با پسوند `.dev` از نصب قدیمی جداست. این بررسی جایگزین بررسی manifest ادغام‌شده نیست.

**این اسکلت قابل استفاده یا مناسب جایگزینی برنامهٔ قدیمی نیست.** حتی در صورت ساخت موفق، نباید release آن را روی دادهٔ کاربر نصب کرد؛ نسخهٔ فعلی داده‌های قبلی را نمایش نمی‌دهد و migration ندارد. debug یک برنامهٔ جدا با دادهٔ جداست و آزمون مهاجرت محسوب نمی‌شود.

## دستورات مورد نظر در محیط مجهز

به JDK کامل 17، Android SDK با platform 35 و دسترسی به مخازن Gradle/Google/Maven نیاز است:

```sh
./gradlew :domain:test :data:testDebugUnitTest
./gradlew :app:assembleDebug
python3 parity/tools/check-android-scaffold.py
```

Suiteهای JUnit برای فراخوانی آزمون‌های موجود اضافه شده‌اند، ولی خود اجرای Gradle و suiteها هنوز تأیید نشده است. آزمون‌های مستقل Kotlin با `parity/tools/check-domain.sh`، `check-backup-reader.sh`، `check-migration-conversion.sh` و `check-csv-kotlin.sh` همچنان جداگانه قابل اجرا هستند.

در sandbox فعلی Android SDK و JDK کامل وجود ندارند و دسترسی `services.gradle.org` و Google Maven مسدود است؛ `./gradlew --version` هنگام دانلود توزیع به خطای SSL رسید. بنابراین **هیچ Gradle build، manifest ادغام‌شده، APK، emulator یا تست دستگاهی انجام نشده است**. بررسی ایستای manifest منبع و خصوصیات wrapper اجرا شده است. wrapper از تگ `v8.9.0` مخزن رسمی Gradle دریافت شد؛ checksum توزیع از دو نسخهٔ منتشرشدهٔ عمومی wrapper تطبیق داده شده، اما در این محیط از منبع checksum رسمی به‌طور مستقل بازیابی نشد. نسخه‌های وابستگی و تنظیمات build پیش از انتشار باید در محیط مجهز بررسی و قفل شوند.

امضای release پیکربندی نشده است. گواهی داخل APK کلید خصوصی نیست. نصب به‌صورت به‌روزرسانی فقط پس از تکمیل مهاجرت، تأیید نسخهٔ نصب‌شده و امضا با keystore اصلی و alias `STAYAR` ممکن است؛ رمز یا کلید نباید در مخزن یا گفتگو ثبت شود. مرحلهٔ امضا و نصب واقعی در این محیط انجام نشده است.
