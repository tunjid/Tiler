As tiling is a pure function that operates on a reactive stream, its configuration can be changed on
the fly.
This lends it well to the following situations:

* Offline-first apps: Tiling delivers targeted updates to only queries that have changed. This works
  well for
  apps which write to the database as the source of truth, and need the UI to update immediately.
  For example
  a viral tweet whose like count updates several times a second.

* Adaptive pagination: The amount of items paginated through can be adjusted dynamically to account
  for app window
  resizing by turning [on](https://github.com/tunjid/Tiler#inputrequest) more pages and increasing
  the
  [limit](https://github.com/tunjid/Tiler#inputlimiter) of data sent to the UI from the paginated
  data available.
  An example is in
  the [Me](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt)
  app.

* Dynamic sort order: The sort order of paginated items can be changed cheaply on a whim by changing
  the
  [order](https://github.com/tunjid/Tiler#inputorder) as this only operates on the data output from
  the tiler, and not
  the entire paginated data set. An example is in the sample in this
  [repository](https://github.com/tunjid/Tiler/blob/develop/common/src/commonMain/kotlin/com/tunjid/demo/common/ui/numbers/advanced/NumberFetching.kt).
