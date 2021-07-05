package com.tunjid.tyler

import org.junit.Assert.assertEquals
import org.junit.Test


class FlattenKtTest {

    @Test
    fun `maintains all items`() {
        val flattened =
            (1..9)
                .mapIndexed { index, int ->
                    index to Tile(
                        flowOnAt = index.toLong(),
                        query = int,
                        item = int.testRange.toList()
                    )
                }.toMap()
                .sortAndFlatten(Int::compareTo)
                .flatten()
                .toList()

        assertEquals(
            (1..9).map { it.testRange }.flatten(),
            flattened
        )
    }

    @Test
    fun `pivots around most recent when limit exists`() {
        val flattened =
            ((1..9).mapIndexed { index, int ->
                index to Tile(
                    flowOnAt = index.toLong(),
                    query = int,
                    item = int.testRange.toList()
                )
            } + (6 downTo 4).mapIndexed { index, int ->
                index to Tile(
                    flowOnAt = index.toLong() + 10L,
                    query = int,
                    item = int.testRange.toList()
                )
            })
                .toMap()
                .pivotSortAndFlatten(Int::compareTo)
                .flatten()
                .take(50)
                .toList()

        assertEquals(
            (2..6).map { it.testRange }.flatten(),
            flattened
        )
    }

}

private fun IntProgression.toTile(
    flowOnAt: (Int) -> Long,
) = mapIndexed { index, int ->
    index to Tile(
        flowOnAt = flowOnAt(index),
        query = TileRequest.On(int),
        item = int.testRange.toList()
    )
}.toMap()

