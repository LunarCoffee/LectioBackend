package dev.lectio

import dev.lectio.model.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.*
import java.io.*

private val client = KMongo.createClient().coroutine
val database = client.getDatabase("LectioDb3")

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) { jackson() }
    install(CORS) { anyHost() }

    routing {
        get("/tests/{id}") {
            val id = call.parameters["id"] ?: "error"
            call.respondFile(
                try {
                    File("resources/tests/test-$id.json")
                } catch (e: NoSuchFileException) {
                    File("resources/tests/test-error.json")
                }
            )
        }

        route("/users") {
            get {
                call.respond(
                    User.col.find().toList().map {
                        it.run {
                            mapOf(
                                "id" to id,
                                "name" to name,
                                "rank" to rank,
                                "difficulty" to difficulty,
                                "sessions" to mutableListOf<Map<String, Any>>().apply {
                                    for (session in sessionHistory) {
                                        this += mapOf(
                                            "correct" to session.correct,
                                            "incorrect" to session.incorrect
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }

            post("/add") {
                val user = call.receive<UserPostWrapper>().run { User(name, rank) }

                User.col.insertOne(user)
                call.respond(user.id)
            }

            route("/{id}") {
                get {
                    val id = call.parameters["id"]?.toInt() ?: return@get
                    val user = User.col.findOne(User::id eq id) ?: return@get

                    call.respond(
                        user.run {
                            mapOf(
                                "id" to id,
                                "name" to name,
                                "rank" to rank,
                                "difficulty" to difficulty,
                                "sessions" to mutableListOf<Map<String, Any>>().apply {
                                    for (session in sessionHistory) {
                                        this += mapOf(
                                            "correct" to session.correct,
                                            "incorrect" to session.incorrect
                                        ).also(::println)
                                    }
                                }
                            )
                        }
                    )
                }

                delete {
                    val id = call.parameters["id"]?.toInt() ?: return@delete
                    User.col.deleteOne(User::id eq id)

                    call.respond(HttpStatusCode.NoContent)
                }

                route("/sessions") {
                    get {
                        val id = call.parameters["id"]?.toInt() ?: return@get
                        val user = User.col.findOne(User::id eq id) ?: return@get

                        call.respond(
                            user.sessionHistory.map {
                                mapOf(
                                    "correct" to it.correct,
                                    "incorrect" to it.incorrect
                                )
                            }
                        )
                    }

                    post("/add") {
                        val id = call.parameters["id"]?.toInt() ?: return@post
                        val user = User.col.findOne(User::id eq id) ?: return@post

                        val (correct, incorrect) = call.receive<SessionStatistics>().run {
                            Pair(correct, incorrect)
                        }

                        user.recordSession(correct, incorrect)
                        call.respond(user.id)
                    }
                }

                route("/difficulty") {
                    get {
                        val id = call.parameters["id"]?.toInt() ?: return@get
                        val user = User.col.findOne(User::id eq id) ?: return@get

                        call.respond(user.difficulty)
                    }

                    post("/set") {
                        val id = call.parameters["id"]?.toInt() ?: return@post
                        val user = User.col.findOne(User::id eq id) ?: return@post

                        val new = call.receive<DifficultyPostWrapper>().new
                        call.respond(
                            if (user.updateDifficulty(new)) {
                                HttpStatusCode.Created
                            } else {
                                HttpStatusCode.BadRequest
                            }
                        )
                    }
                }
            }
        }
    }
}
