package com.anderei.yafm.util

/** Quotes a string for safe use as one POSIX shell argument. */
fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
