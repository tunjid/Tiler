package com.tunjid.tiler

import org.junit.Assert.assertEquals
import org.junit.Test


class TilerKtTest {

    @Test
    fun `maintains all items`() {
        val tiled =
            (1..9)
                .mapIndexed { index, int ->
                    Tile.Output.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            item = int.testRange.toList()
                        )
                    )
                }
                .fold(
                    initial = Tiler(flattener = Tile.Flattener.Sorted(comparator = Int::compareTo)),
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
                Tile.Output.Data(
                    query = int,
                    tile = Tile(
                        flowOnAt = index.toLong(),
                        item = int.testRange.toList()
                    )
                )
            } + (6 downTo 4).mapIndexed { index, int ->
                Tile.Output.Data(
                    query = int,
                    tile = Tile(
                        flowOnAt = index.toLong() + 10L,
                        item = int.testRange.toList()
                    )
                )
            })
                .fold(
                    initial = Tiler(
                        flattener = Tile.Flattener.PivotSorted(
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
