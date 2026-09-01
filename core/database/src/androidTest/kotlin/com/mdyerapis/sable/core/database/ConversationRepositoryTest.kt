package com.mdyerapis.sable.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdyerapis.sable.core.database.chat.ChatDatabase
import com.mdyerapis.sable.core.database.chat.ConversationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationRepositoryTest {
    private lateinit var db: ChatDatabase
    private lateinit var repository: ConversationRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConversationRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndList_roundTripsSummary() = runBlocking {
        val id = repository.createConversation(modelId = "gemma-3n-E2B-it", mode = "Backend")
        val summaries = repository.conversations.first()
        assertEquals(1, summaries.size)
        assertEquals(id, summaries.first().id)
        assertEquals("Backend", summaries.first().mode)
        assertEquals("gemma-3n-E2B-it", summaries.first().modelId)
    }

    @Test
    fun appendMessages_orderedByCreatedAt() = runBlocking {
        val id = repository.createConversation(modelId = null, mode = "Backend")
        repository.appendMessage(id, "m1", "user", "first")
        repository.appendMessage(id, "m2", "assistant", "second")
        val messages = repository.messagesFor(id).first()
        assertEquals(2, messages.size)
        assertEquals("first", messages[0].content)
        assertEquals("second", messages[1].content)
    }

    @Test
    fun deleteConversation_removesMessages() = runBlocking {
        val id = repository.createConversation(modelId = null, mode = "OnDevice")
        repository.appendMessage(id, "m1", "user", "hello")
        assertEquals(1, repository.messagesFor(id).first().size)

        repository.deleteConversation(id)
        assertTrue(repository.conversations.first().isEmpty())
        assertTrue(repository.messagesFor(id).first().isEmpty())
    }

    @Test
    fun setServerConversationId_persisted() = runBlocking {
        val id = repository.createConversation(modelId = null, mode = "Backend")
        repository.setServerConversationId(id, "server-42")
        val summary = repository.conversations.first().first()
        assertEquals("server-42", summary.serverConversationId)
    }
}
