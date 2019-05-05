package dev.lectio.model

import dev.lectio.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.litote.kmongo.*

class User(val name: String, val rank: Int) {
    // This tracks the number of incorrectly pronounced words in each session.
    val sessionHistory = mutableListOf<SessionStatistics>()
    val id = curId.getAndIncrement().apply {
        GlobalScope.launch {
            if (idCol.find().toList().isEmpty()) {
                idCol.insertOne(curId)
            } else {
                idCol.updateOne(AtomicLong::value eq this@apply, curId)
            }
        }
    }

    fun recordSession(correct: List<String>, incorrect: List<String>) {
        sessionHistory += SessionStatistics(correct, incorrect)
        GlobalScope.launch { col.updateOne(::id eq id, this@User) }
    }

    companion object {
        val col = database.getCollection<User>("Users2")

        // Incrementing unique ID.
        private val idCol = database.getCollection<AtomicLong>("UserId2")
        private val curId = runBlocking { idCol.find().first() ?: atomic(0L) }
    }
}
