package com.ravert.guitar_trainer.db

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TabRequestsRepositoryTest {
    @Test
    fun requestNamesUseConservativeDuplicateNormalization() {
        assertEquals("wake me up", normalizeTabRequestText("  Wake   Me Up "))
        assertEquals("guns n' roses", normalizeTabRequestText("Guns N' Roses"))
    }

    @Test
    fun popularSortUsesVotesThenOldestRequest() {
        val newerPopular = request(votes = 5, createdAt = 200)
        val olderPopular = request(votes = 5, createdAt = 100)
        val lessPopular = request(votes = 4, createdAt = 50)

        assertEquals(
            listOf(olderPopular, newerPopular, lessPopular),
            sortRequests(
                listOf(lessPopular, newerPopular, olderPopular),
                TabRequestSort.POPULAR,
                completedLast = false,
            ),
        )
    }

    @Test
    fun adminSortAlwaysPlacesCompletedRequestsLast() {
        val completed = request(status = TabRequestStatusCompleted, createdAt = 300)
        val active = request(status = TabRequestStatusRequested, createdAt = 100)

        assertEquals(
            listOf(active, completed),
            sortRequests(
                listOf(completed, active),
                TabRequestSort.NEWEST,
                completedLast = true,
            ),
        )
    }

    private fun request(
        votes: Int = 0,
        createdAt: Long,
        status: String = TabRequestStatusRequested,
    ) = TabRequestRecord(
        uuid = UUID.randomUUID(),
        requestedByUserUuid = null,
        requesterEmail = null,
        artistName = "Artist",
        songName = "Song",
        details = null,
        status = status,
        completedSongUuid = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        completedAt = null,
        voteCount = votes,
        currentUserHasVoted = false,
    )
}
