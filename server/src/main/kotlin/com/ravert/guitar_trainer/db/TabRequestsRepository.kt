package com.ravert.guitar_trainer.db

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

const val TabRequestStatusRequested = "requested"
const val TabRequestStatusInProgress = "in_progress"
const val TabRequestStatusCompleted = "completed"
val TabRequestStatuses = setOf(
    TabRequestStatusRequested,
    TabRequestStatusInProgress,
    TabRequestStatusCompleted,
)

enum class TabRequestSort {
    POPULAR,
    NEWEST,
    OLDEST,
}

data class TabRequestRecord(
    val uuid: UUID,
    val requestedByUserUuid: UUID?,
    val requesterEmail: String?,
    val artistName: String,
    val songName: String,
    val details: String?,
    val status: String,
    val completedSongUuid: UUID?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val voteCount: Int,
    val currentUserHasVoted: Boolean,
)

data class SubmitTabRequestResult(
    val requestUuid: UUID,
    val created: Boolean,
    val completed: Boolean,
)

enum class TabRequestVoteResult {
    ADDED,
    ALREADY_VOTED,
    REQUEST_NOT_FOUND,
    REQUEST_COMPLETED,
}

enum class UpdateTabRequestResult {
    UPDATED,
    REQUEST_NOT_FOUND,
    COMPLETED_SONG_NOT_FOUND,
}

class TabRequestsRepository {
    fun submitRequest(
        userUuid: UUID,
        artistName: String,
        songName: String,
        details: String?,
        now: Long,
    ): SubmitTabRequestResult = transaction {
        val normalizedArtistName = normalizeTabRequestText(artistName)
        val normalizedSongName = normalizeTabRequestText(songName)
        val newRequestUuid = UUID.randomUUID()
        val insert = SongTabRequestsTable.insertIgnore {
            it[uuid] = newRequestUuid
            it[requestedByUserUuid] = userUuid
            it[SongTabRequestsTable.artistName] = artistName
            it[SongTabRequestsTable.songName] = songName
            it[SongTabRequestsTable.normalizedArtistName] = normalizedArtistName
            it[SongTabRequestsTable.normalizedSongName] = normalizedSongName
            it[SongTabRequestsTable.details] = details
            it[status] = TabRequestStatusRequested
            it[completedSongUuid] = null
            it[createdAt] = now
            it[updatedAt] = now
            it[completedAt] = null
        }

        val request = SongTabRequestsTable
            .selectAll()
            .where {
                (SongTabRequestsTable.normalizedArtistName eq normalizedArtistName) and
                    (SongTabRequestsTable.normalizedSongName eq normalizedSongName)
            }
            .single()
        val requestUuid = request[SongTabRequestsTable.uuid]
        val completed = request[SongTabRequestsTable.status] == TabRequestStatusCompleted

        if (!completed) {
            addVoteIgnoringDuplicate(requestUuid, userUuid, now)
        }

        SubmitTabRequestResult(
            requestUuid = requestUuid,
            created = insert.insertedCount > 0,
            completed = completed,
        )
    }

    fun listRequests(
        currentUserUuid: UUID?,
        includeCompleted: Boolean,
        status: String?,
        search: String?,
        sort: TabRequestSort,
    ): List<TabRequestRecord> = transaction {
        val voteCounts = SongTabRequestVotesTable
            .selectAll()
            .groupingBy { it[SongTabRequestVotesTable.requestUuid] }
            .eachCount()
        val currentUserVotes = if (currentUserUuid == null) {
            emptySet()
        } else {
            SongTabRequestVotesTable
                .selectAll()
                .where { SongTabRequestVotesTable.userUuid eq currentUserUuid }
                .mapTo(mutableSetOf()) { it[SongTabRequestVotesTable.requestUuid] }
        }
        val normalizedSearch = search?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        val requests = SongTabRequestsTable
            .join(
                UsersTable,
                JoinType.LEFT,
                SongTabRequestsTable.requestedByUserUuid,
                UsersTable.uuid,
            )
            .selectAll()
            .map { row ->
                row.toTabRequestRecord(
                    voteCount = voteCounts[row[SongTabRequestsTable.uuid]] ?: 0,
                    currentUserHasVoted = row[SongTabRequestsTable.uuid] in currentUserVotes,
                )
            }
            .filter { request ->
                (includeCompleted || request.status != TabRequestStatusCompleted) &&
                    (status == null || request.status == status) &&
                    (normalizedSearch == null ||
                        request.artistName.lowercase().contains(normalizedSearch) ||
                        request.songName.lowercase().contains(normalizedSearch))
            }

        sortRequests(requests, sort, completedLast = includeCompleted)
    }

    fun findRequest(requestUuid: UUID, currentUserUuid: UUID?): TabRequestRecord? = transaction {
        val row = SongTabRequestsTable
            .join(
                UsersTable,
                JoinType.LEFT,
                SongTabRequestsTable.requestedByUserUuid,
                UsersTable.uuid,
            )
            .selectAll()
            .where { SongTabRequestsTable.uuid eq requestUuid }
            .singleOrNull() ?: return@transaction null

        val voteCount = SongTabRequestVotesTable
            .selectAll()
            .where { SongTabRequestVotesTable.requestUuid eq requestUuid }
            .count()
            .toInt()
        val currentUserHasVoted = currentUserUuid != null && !SongTabRequestVotesTable
            .selectAll()
            .where {
                (SongTabRequestVotesTable.requestUuid eq requestUuid) and
                    (SongTabRequestVotesTable.userUuid eq currentUserUuid)
            }
            .limit(1)
            .empty()

        row.toTabRequestRecord(voteCount, currentUserHasVoted)
    }

    fun addVote(requestUuid: UUID, userUuid: UUID, now: Long): TabRequestVoteResult = transaction {
        val requestStatus = SongTabRequestsTable
            .selectAll()
            .where { SongTabRequestsTable.uuid eq requestUuid }
            .singleOrNull()
            ?.get(SongTabRequestsTable.status)
            ?: return@transaction TabRequestVoteResult.REQUEST_NOT_FOUND
        if (requestStatus == TabRequestStatusCompleted) {
            return@transaction TabRequestVoteResult.REQUEST_COMPLETED
        }

        val inserted = addVoteIgnoringDuplicate(requestUuid, userUuid, now)
        if (inserted) TabRequestVoteResult.ADDED else TabRequestVoteResult.ALREADY_VOTED
    }

    fun removeVote(requestUuid: UUID, userUuid: UUID): Int = transaction {
        SongTabRequestVotesTable.deleteWhere {
            (SongTabRequestVotesTable.requestUuid eq requestUuid) and
                (SongTabRequestVotesTable.userUuid eq userUuid)
        }
    }

    fun updateRequest(
        requestUuid: UUID,
        status: String,
        completedSongUuid: UUID?,
        now: Long,
    ): UpdateTabRequestResult = transaction {
        val existingRequest = SongTabRequestsTable
            .selectAll()
            .where { SongTabRequestsTable.uuid eq requestUuid }
            .singleOrNull()
            ?: return@transaction UpdateTabRequestResult.REQUEST_NOT_FOUND

        if (completedSongUuid != null) {
            val songExists = !SongsTable
                .selectAll()
                .where { SongsTable.id eq completedSongUuid }
                .limit(1)
                .empty()
            if (!songExists) return@transaction UpdateTabRequestResult.COMPLETED_SONG_NOT_FOUND
        }

        val updated = SongTabRequestsTable.update({ SongTabRequestsTable.uuid eq requestUuid }) {
            it[SongTabRequestsTable.status] = status
            it[SongTabRequestsTable.completedSongUuid] = completedSongUuid
            it[updatedAt] = now
            it[completedAt] = if (status == TabRequestStatusCompleted) {
                existingRequest[SongTabRequestsTable.completedAt] ?: now
            } else {
                null
            }
        }
        if (updated == 0) UpdateTabRequestResult.REQUEST_NOT_FOUND else UpdateTabRequestResult.UPDATED
    }

    private fun addVoteIgnoringDuplicate(requestUuid: UUID, userUuid: UUID, now: Long): Boolean {
        val insert = SongTabRequestVotesTable.insertIgnore {
            it[uuid] = UUID.randomUUID()
            it[SongTabRequestVotesTable.requestUuid] = requestUuid
            it[SongTabRequestVotesTable.userUuid] = userUuid
            it[createdAt] = now
        }
        return insert.insertedCount > 0
    }

    private fun ResultRow.toTabRequestRecord(
        voteCount: Int,
        currentUserHasVoted: Boolean,
    ) = TabRequestRecord(
        uuid = this[SongTabRequestsTable.uuid],
        requestedByUserUuid = this[SongTabRequestsTable.requestedByUserUuid],
        requesterEmail = this.getOrNull(UsersTable.email),
        artistName = this[SongTabRequestsTable.artistName],
        songName = this[SongTabRequestsTable.songName],
        details = this[SongTabRequestsTable.details],
        status = this[SongTabRequestsTable.status],
        completedSongUuid = this[SongTabRequestsTable.completedSongUuid],
        createdAt = this[SongTabRequestsTable.createdAt],
        updatedAt = this[SongTabRequestsTable.updatedAt],
        completedAt = this[SongTabRequestsTable.completedAt],
        voteCount = voteCount,
        currentUserHasVoted = currentUserHasVoted,
    )
}

internal fun normalizeTabRequestText(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")

internal fun sortRequests(
    requests: List<TabRequestRecord>,
    sort: TabRequestSort,
    completedLast: Boolean,
): List<TabRequestRecord> {
    val requestedSort = when (sort) {
        TabRequestSort.POPULAR -> compareByDescending<TabRequestRecord> { it.voteCount }
            .thenBy { it.createdAt }
        TabRequestSort.NEWEST -> compareByDescending { it.createdAt }
        TabRequestSort.OLDEST -> compareBy { it.createdAt }
    }
    val comparator = if (completedLast) {
        compareBy<TabRequestRecord> { it.status == TabRequestStatusCompleted }.then(requestedSort)
    } else {
        requestedSort
    }
    return requests.sortedWith(comparator)
}
