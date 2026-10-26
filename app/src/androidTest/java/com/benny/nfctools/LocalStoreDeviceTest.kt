package com.benny.nfctools

import androidx.test.platform.app.InstrumentationRegistry
import com.benny.nfctools.data.LocalStore
import com.benny.nfctools.data.TagSnapshot
import com.benny.nfctools.data.SavedCard
import com.benny.nfctools.data.CardCategory
import org.junit.Assert.*
import org.junit.Test

class LocalStoreDeviceTest {
    @Test fun repeatedHistoryWritesAndClearAreDurable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalStore(context)
        try {
            val card = TagSnapshot("TEST", listOf("NfcA"), "Test tag", null, null, false, false, emptyMap(), null)
            store.saveHistory(listOf(card))
            assertEquals(card, store.loadHistory().single())
            store.saveHistory(List(60) { card.copy(timestamp = it.toLong()) })
            assertEquals(50, store.loadHistory().size)
            store.saveHistory(List(3) { card.copy(timestamp = it.toLong()) })
            assertEquals(3, LocalStore(context).loadHistory().size)
            store.saveHistory(emptyList())
            assertTrue(LocalStore(context).loadHistory().isEmpty())
        } finally { store.saveHistory(emptyList()) }
    }
    @Test fun cardRenameAndCategorySurviveReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalStore(context)
        val original = store.loadCards()
        val tag = TagSnapshot("TEST-PERSISTENCE", listOf("NfcA"), "Test", null, null, false, false, emptyMap(), null)
        try {
            val card = SavedCard(name = "未命名", tag = tag)
            store.saveCards(original + card)
            val renamed = card.copy(name = "家门", category = CardCategory.ACCESS)
            store.saveCards(original + renamed)
            assertEquals(renamed, LocalStore(context).loadCards().last())
        } finally { store.saveCards(original) }
    }
}
