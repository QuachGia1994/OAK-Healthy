package com.example.supplementtracker.presentation.home

import org.junit.Assert.assertTrue
import org.junit.Test

class ShareBitmapLifecycleTest {
    @Test
    fun `useAndRelease releases after successful write`() {
        var released = false

        useAndRelease(
            resource = Unit,
            release = { released = true },
        ) { "written" }

        assertTrue(released)
    }

    @Test
    fun `useAndRelease releases when write throws`() {
        var released = false

        runCatching {
            useAndRelease(
                resource = Unit,
                release = { released = true },
            ) { error("write failed") }
        }

        assertTrue(released)
    }
}
