package com.saintm.smartlauncher.data

/** Return the latest item from at most three distinct, currently permitted media apps. */
fun visibleMediaItems(
    history: List<MediaHistoryItem>,
    filterEnabled: Boolean,
    allowedPackages: Set<String>,
): List<MediaHistoryItem> {
    val seen = mutableSetOf<String>()
    return history.asSequence()
        .filter { !filterEnabled || it.packageName in allowedPackages }
        .filter { seen.add(it.packageName) }
        .take(3)
        .toList()
}

/** Move one favourite by one position; invalid moves return an unchanged copy. */
fun moveFavourite(packages: List<String>, packageName: String, delta: Int): List<String> {
    val from = packages.indexOf(packageName)
    val to = from + delta
    if (from < 0 || to !in packages.indices) return packages.toList()
    return packages.toMutableList().apply {
        val moved = removeAt(from)
        add(to, moved)
    }
}
