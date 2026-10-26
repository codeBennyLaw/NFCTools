package com.benny.nfctools.data

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalStore(context: Context) {
    private val historyFile = AtomicFile(File(context.filesDir, "history.json"))
    private val cardsFile = AtomicFile(File(context.filesDir, "cards.json"))
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    fun loadHistory(): List<TagSnapshot> {
        if (!historyFile.baseFile.exists()) return emptyList()
        val array = JSONArray(historyFile.openRead().bufferedReader().use { it.readText() })
        return (0 until array.length()).map { snapshot(array.getJSONObject(it)) }.take(50)
    }
    fun saveHistory(history: List<TagSnapshot>) {
        val bytes = JSONArray().apply { history.take(50).forEach { put(json(it)) } }.toString().toByteArray()
        val stream = historyFile.startWrite()
        try { stream.write(bytes); historyFile.finishWrite(stream) }
        catch (e: Exception) { historyFile.failWrite(stream); throw e }
    }
    fun draft(): List<DraftRecord> = runCatching {
        val a = JSONArray(prefs.getString("draft", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            DraftRecord(o.getString("id"), RecordKind.valueOf(o.getString("kind")), o.getString("value"),
                o.optString("secondary"), o.optString("tertiary"), o.optString("mime", "application/octet-stream"), o.optString("base64"))
        }
    }.getOrDefault(emptyList())
    fun saveDraft(records: List<DraftRecord>, imported: String?, sequence: Boolean = false) {
        val a = JSONArray().apply { records.forEach { d ->
            put(JSONObject().put("id", d.id).put("kind", d.kind.name).put("value", d.value)
                .put("secondary", d.secondary).put("tertiary", d.tertiary).put("mime", d.mime).put("base64", d.base64))
        } }
        prefs.edit { putString("draft", a.toString()); putString("imported", imported); putBoolean("sequence_mode", sequence) }
    }
    fun sequenceMode(): Boolean = prefs.getBoolean("sequence_mode", false)
    fun imported(): String? = prefs.getString("imported", null)
    fun theme(): String = prefs.getString("theme", "system") ?: "system"
    fun saveTheme(theme: String) { prefs.edit { putString("theme", theme) } }
    fun quickEntry(): DraftRecord = runCatching {
        val o = JSONObject(prefs.getString("quick_entry", "{}") ?: "{}")
        DraftRecord(id = o.getString("id"), kind = RecordKind.valueOf(o.getString("kind")), value = o.optString("value"), secondary = o.optString("secondary"), tertiary = o.optString("tertiary"))
    }.getOrElse { DraftRecord() }
    fun saveQuickEntry(entry: DraftRecord) {
        val o = JSONObject().put("id", entry.id).put("kind", entry.kind.name).put("value", entry.value).put("secondary", entry.secondary).put("tertiary", entry.tertiary)
        prefs.edit { putString("quick_entry", o.toString()) }
    }
    fun loadCards(): List<SavedCard> {
        if (!cardsFile.baseFile.exists()) return emptyList()
        val a = JSONArray(cardsFile.openRead().bufferedReader().use { it.readText() })
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SavedCard(o.getString("id"), o.getString("name"), CardCategory.valueOf(o.getString("category")), snapshot(o.getJSONObject("tag")))
        }
    }
    fun saveCards(cards: List<SavedCard>) {
        require(cards.size <= 100) { "卡包最多保存 100 张卡片" }
        val bytes = JSONArray().apply { cards.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category.name).put("tag", json(it.tag))) } }.toString().toByteArray()
        val stream = cardsFile.startWrite()
        try { stream.write(bytes); cardsFile.finishWrite(stream) }
        catch (e: Exception) { cardsFile.failWrite(stream); throw e }
    }

    companion object {
        fun json(s: TagSnapshot) = JSONObject().put("version", 1).put("uid", s.uid)
            .put("technologies", JSONArray(s.technologies)).put("type", s.type)
            .put("capacity", s.capacity ?: JSONObject.NULL).put("writable", s.writable ?: JSONObject.NULL)
            .put("canLock", s.canLock).put("formatable", s.formatable).put("details", JSONObject(s.details))
            .put("messageBase64", s.messageBase64 ?: JSONObject.NULL).put("timestamp", s.timestamp).put("action", s.action).put("sample", s.sample)
            .put("memory", s.memory?.let { m -> JSONObject().put("format", m.format).put("blockSize", m.blockSize)
                .put("blocks", JSONArray().apply { m.blocks.forEach { put(it ?: JSONObject.NULL) } }).put("notes", JSONArray(m.notes)) } ?: JSONObject.NULL)

        private fun snapshot(o: JSONObject): TagSnapshot {
            val a = o.getJSONArray("technologies")
            val d = o.getJSONObject("details")
            return TagSnapshot(o.getString("uid"), (0 until a.length()).map { a.getString(it) }, o.getString("type"),
                if (o.isNull("capacity")) null else o.getInt("capacity"),
                if (o.isNull("writable")) null else o.getBoolean("writable"), o.getBoolean("canLock"), o.getBoolean("formatable"),
                d.keys().asSequence().associateWith { d.getString(it) },
                if (o.isNull("messageBase64")) null else o.getString("messageBase64"), o.getLong("timestamp"), o.getString("action"), o.optBoolean("sample"),
                o.optJSONObject("memory")?.let { m ->
                    val blocks = m.getJSONArray("blocks"); val notes = m.optJSONArray("notes") ?: JSONArray()
                    MemoryDump(m.getString("format"), m.getInt("blockSize"), (0 until blocks.length()).map { if (blocks.isNull(it)) null else blocks.getString(it) }, (0 until notes.length()).map { notes.getString(it) })
                })
        }
    }
}
