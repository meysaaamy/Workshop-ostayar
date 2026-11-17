package com.workshop.ledger.data.backup

fun main(args: Array<String>) {
    fun read(s: String, mode: LegacyBackupMode = LegacyBackupMode.FULL) = LegacyBackupReader.read(s.toByteArray(Charsets.UTF_8), mode)
    fun bad(s: String, reason: BackupReadException.Reason, mode: LegacyBackupMode = LegacyBackupMode.FULL) {
        check((runCatching { read(s, mode) }.exceptionOrNull() as? BackupReadException)?.reason == reason) { "Expected $reason" }
    }
    val full = """{"app":"stayar","kind":"full","version":2,"exportedAt":"1403/01/01","state":{"version":3,"projects":[{"users":[],"attendance":{"1403/01/01":{"u":{"t":"h","h":1.2500}}},"unknown":"سلام"}]}}"""
    val doc = read("\uFEFF$full")
    val projects = doc.payload.fields["projects"] as JsonValue.Array
    val project = projects.values.single() as JsonValue.Object
    val attendance = project.fields["attendance"] as JsonValue.Object
    val day = attendance.fields["1403/01/01"] as JsonValue.Object
    val user = day.fields["u"] as JsonValue.Object
    check(user.fields["h"] == JsonValue.Number("1.2500"))
    check(project.fields["unknown"] == JsonValue.Text("سلام"))
    val p = read("""{"app":"stayar","kind":"project","version":2,"project":{"users":[],"name":"پروژه"}}""", LegacyBackupMode.PROJECT)
    check(p.payload.fields["name"] == JsonValue.Text("پروژه"))
    check(read("""{"projects":[]}""").mode == LegacyBackupMode.FULL)
    check(read("""{"users":[]}""", LegacyBackupMode.PROJECT).mode == LegacyBackupMode.PROJECT)
    check(read("""{"projects":[{"users":[]}]}""", LegacyBackupMode.PROJECT).payload.fields["users"] is JsonValue.Array)
    bad("""{"app":"stayar","kind":"full","version":3,"state":{"projects":[]}}""", BackupReadException.Reason.UNSUPPORTED_ENVELOPE)
    bad("""{"app":"other","kind":"full","version":2,"state":{"projects":[]}}""", BackupReadException.Reason.UNSUPPORTED_ENVELOPE)
    bad("""{"projects":[],"projects":[]}""", BackupReadException.Reason.INVALID_JSON)
    for (badJson in listOf("{", "[] trailing", "{\"projects\":[1,]}", "{\"projects\":[01]}", "{\"projects\":[1e]}", "{\"projects\":[\"\\uD800\"]}"))
        bad(badJson, BackupReadException.Reason.INVALID_JSON)
    bad("""{"projects":"wrong"}""", BackupReadException.Reason.INVALID_SHAPE)
    bad("""{"projects":[]} """ + "[".repeat(129), BackupReadException.Reason.INVALID_JSON)
    bad("{\"projects\":[" + "[".repeat(130) + "0" + "]".repeat(130) + "]}", BackupReadException.Reason.RESOURCE_LIMIT)
    bad("{\"projects\":[" + "1".repeat(129) + "]}", BackupReadException.Reason.RESOURCE_LIMIT)
    check((runCatching { LegacyBackupReader.read(byteArrayOf(0xc3.toByte(), 0x28), LegacyBackupMode.FULL) }.exceptionOrNull() as BackupReadException).reason == BackupReadException.Reason.INVALID_UTF8)
    check((runCatching { LegacyBackupReader.read(ByteArray(LegacyBackupReader.MAX_BYTES + 1), LegacyBackupMode.FULL) }.exceptionOrNull() as BackupReadException).reason == BackupReadException.Reason.SIZE_LIMIT)
    require(args.size == 2)
    val fromApkFull = LegacyBackupReader.read(java.io.File(args[0]).readBytes(), LegacyBackupMode.FULL)
    val fromApkProject = LegacyBackupReader.read(java.io.File(args[1]).readBytes(), LegacyBackupMode.PROJECT)
    check((fromApkFull.payload.fields["projects"] as JsonValue.Array).values.size == 1)
    check(fromApkProject.payload.fields["name"] == JsonValue.Text("کارگاه نمونه"))
    println("Checked real legacy-export fixtures, JSON backup classification and bounded parsing; no validation, restore, encryption or persistence tested.")
}
