package androidx.sqlite.db
import java.io.Closeable
interface Cursor : Closeable { fun moveToNext(): Boolean; fun getString(index: Int): String; fun getLong(index: Int): Long; fun isNull(index: Int): Boolean }
interface SupportSQLiteDatabase {
    fun query(sql: String): Cursor
    fun execSQL(sql: String)
    fun execSQL(sql: String, bindArgs: Array<out Any?>)
}
