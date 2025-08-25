package com.github.svk014.autodbgjetbrains.leetcode.buggy_test.snakes_and_ladders

class SnakesAndLadderTest : junit.framework.TestCase() {

    fun testSnakesAndLadders() {
        val board = arrayOf<IntArray?>(
            intArrayOf(-1, 83, -1, 46, -1, -1, -1, -1, 40, -1),
            intArrayOf(-1, 29, -1, -1, -1, 51, -1, 18, -1, -1),
            intArrayOf(-1, 35, 31, 51, -1, 6, -1, 40, -1, -1),
            intArrayOf(-1, -1, -1, 28, -1, 36, -1, -1, -1, -1),
            intArrayOf(-1, -1, -1, -1, 44, -1, -1, 84, -1, -1),
            intArrayOf(-1, -1, -1, 31, -1, 98, 27, 94, 74, -1),
            intArrayOf(4, -1, -1, 46, 3, 14, 7, -1, 84, 67),
            intArrayOf(-1, -1, -1, -1, 2, 72, -1, -1, 86, -1),
            intArrayOf(-1, 32, -1, -1, -1, -1, -1, -1, -1, 19),
            intArrayOf(-1, -1, -1, -1, -1, 72, 46, -1, 92, 6),
        )

        val solution = Solution()
        val result = solution.snakesAndLadders(board)
        println("Minimum moves to reach the last cell: $result")
    }
}