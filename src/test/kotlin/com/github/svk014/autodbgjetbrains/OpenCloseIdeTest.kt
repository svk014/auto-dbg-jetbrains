package com.github.svk014.autodbgjetbrains

class OpenCloseIdeTest: junit.framework.TestCase() {

    fun testOpenAndCloseIde() {
        val a = 1
        val b = 2
        val c = a + b
        assertEquals(3, c)
    }
}
