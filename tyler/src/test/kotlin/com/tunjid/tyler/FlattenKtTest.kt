package com.tunjid.tyler

import org.junit.Assert.assertEquals
import org.junit.Test


class FlattenKtTest {

    @Test
    fun `maintains all items`() {
        val tiled =
            (1..9)
                .mapIndexed { index, int ->
                    Result.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            query = int,
                            item = int.testRange.toList()
                        )
                    )
                }
                .fold(
                    initial = Flatten(itemOrder = TileRequest.ItemOrder.Sort(comparator = Int::compareTo)),
                    operation = Flatten<Int, List<Int>>::add
                )
                .items()
                .flatten()

        assertEquals(
            (1..9).map(Int::testRange).flatten(),
            tiled
        )
    }

    @Test
    fun `pivots around most recent when limit exists`() {
        val tiles =
            ((1..9).mapIndexed { index, int ->
                Result.Data(
                    query = int,
                    tile = Tile(
                        flowOnAt = index.toLong(),
                        query = int,
                        item = int.testRange.toList()
                    )
                )
            } + (6 downTo 4).mapIndexed { index, int ->
                Result.Data(
                    query = int,
                    tile = Tile(
                        flowOnAt = index.toLong() + 10L,
                        query = int,
                        item = int.testRange.toList()
                    )
                )
            })
                .fold(
                    initial = Flatten(
                        itemOrder = TileRequest.ItemOrder.PivotedSort(
                            comparator = Int::compareTo,
                            limiter = { items -> items.fold(0) { count, list -> count + list.size } >= 50 }
                        )
                    ),
                    operation = Flatten<Int, List<Int>>::add
                )
                .items()
                .flatten()

        assertEquals(
            (2..6).map(Int::testRange).flatten(),
            tiles
        )
    }

}
