package com.benny.nfctools.data

import java.util.Locale

object CardSnapshots {
    private fun uidKey(uid: String): String? {
        val value = uid.filterNot { it.isWhitespace() || it == ':' || it == '-' }.uppercase(Locale.ROOT)
        return value.takeIf { it.length >= 2 && it.length % 2 == 0 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
    }

    fun sameCard(first: TagSnapshot, second: TagSnapshot): Boolean {
        val key = uidKey(first.uid) ?: return false
        return key == uidKey(second.uid)
    }

    fun latest(saved: TagSnapshot, history: List<TagSnapshot>, current: TagSnapshot? = null): TagSnapshot =
        (listOfNotNull(current) + history + saved)
            .filter { !it.sample && sameCard(saved, it) && "NDEF 读取" !in it.details }
            .maxByOrNull { it.timestamp } ?: saved
}
