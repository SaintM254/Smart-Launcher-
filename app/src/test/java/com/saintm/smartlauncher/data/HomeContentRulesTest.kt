package com.saintm.smartlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentRulesTest {
    @Test
    fun onlyTheLatestItemForThreeDistinctAppsIsShown() {
        val history = listOf(
            item("netflix", "Newest Netflix"),
            item("youtube", "Newest YouTube"),
            item("netflix", "Older Netflix"),
            item("music", "Music"),
            item("prime", "Prime"),
        )

        assertEquals(
            listOf("Newest Netflix", "Newest YouTube", "Music"),
            visibleMediaItems(history, filterEnabled = false, allowedPackages = emptySet()).map { it.title },
        )
    }

    @Test
    fun mediaFilterSkipsBlockedAppsBeforeTakingThree() {
        val history = listOf(item("blocked", "Hidden"), item("one", "One"), item("two", "Two"), item("three", "Three"), item("four", "Four"))

        assertEquals(
            listOf("One", "Two", "Three"),
            visibleMediaItems(history, filterEnabled = true, allowedPackages = setOf("one", "two", "three", "four")).map { it.title },
        )
    }

    @Test
    fun favouriteMoveDoesNotWrapAtEdges() {
        val original = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveFavourite(original, "b", -1))
        assertEquals(listOf("a", "c", "b"), moveFavourite(original, "b", 1))
        assertEquals(original, moveFavourite(original, "a", -1))
        assertEquals(original, moveFavourite(original, "c", 1))
    }

    private fun item(packageName: String, title: String) = MediaHistoryItem(
        packageName = packageName,
        appName = packageName,
        title = title,
    )
}
