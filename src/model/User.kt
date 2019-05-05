package dev.lectio.model

import dev.lectio.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.litote.kmongo.*

class User(val name: String, val rank: Int) {
    // This tracks the number of incorrectly pronounced words in each session.
    val sessionHistory = mutableListOf<SessionStatistics>()

    // The difficulty is a number between 0 and 4 (inclusive) that represents the difficulty of the
    // lessons and practice sessions provided.
    var difficulty = 0

    val id = curId.getAndIncrement().apply {
        GlobalScope.launch {
            if (idCol.find().toList().isEmpty()) {
                idCol.insertOne(curId)
            } else {
                idCol.updateOne(AtomicLong::value eq this@apply, curId)
            }
        }
    }

    fun updateDifficulty(new: Int): Boolean {
        if (new !in 0..4) {
            return false
        }
        difficulty = new
        updateDbObj()
        return true
    }

    fun recordSession(correct: List<String>, incorrect: List<String>) {
        sessionHistory += SessionStatistics(correct, incorrect)
        updateDbObj()
    }

    private fun updateDbObj() = GlobalScope.launch { col.updateOne(::id eq id, this@User) }

    companion object {
        val col = database.getCollection<User>("Users2")

        // Incrementing unique ID.
        private val idCol = database.getCollection<AtomicLong>("UserId2")
        private val curId = runBlocking { idCol.find().first() ?: atomic(0L) }
    }
}
