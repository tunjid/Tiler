package com.tunjid.tyler

internal val Int.testRange get() = this.times(10).rangeTo(this.times(10) + 9)