package com.tunjid.tyler

import org.junit.Assert.assertEquals
import org.junit.Test


class TilerKtTest {

    @Test
    fun `maintains all items`() {
        val tiled =
            (1..9)
                .mapIndexed { index, int ->
                    Output.Data(
                        query = int,
                        tile = TileData(
                            flowOnAt = index.toLong(),
                            query = int,
                            item = int.testRange.toList()
                        )
                    )
                }
                .fold(
                    initial = Tiler(order = Tile.Order.Sorted(comparator = Int::compareTo)),
                    operation = Tiler<Int, List<Int>>::add
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
                Output.Data(
                    query = int,
                    tile = TileData(
                        flowOnAt = index.toLong(),
                        query = int,
                        item = int.testRange.toList()
                    )
                )
            } + (6 downTo 4).mapIndexed { index, int ->
                Output.Data(
                    query = int,
                    tile = TileData(
                        flowOnAt = index.toLong() + 10L,
                        query = int,
                        item = int.testRange.toList()
                    )
                )
            })
                .fold(
                    initial = Tiler(
                        order = Tile.Order.PivotSorted(
                            comparator = Int::compareTo,
                            limiter = { items -> items.fold(0) { count, list -> count + list.size } >= 50 }
                        )
                    ),
                    operation = Tiler<Int, List<Int>>::add
                )
                .items()
                .flatten()

        assertEquals(
            (2..6).map(Int::testRange).flatten(),
            tiles
        )
    }

}
