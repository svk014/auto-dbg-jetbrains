package com.github.svk014.autodbgjetbrains.leetcode.buggy_test.snakes_and_ladders

class Solution2 {
    fun snakesAndLadders(board: Array<IntArray?>): Int {
        val n = board.size
        val targetIdx = n * n - 1

        // Build flattened board: index 0 -> square 1
        val flat = IntArray(n * n)
        var k = 0
        var leftToRight = true
        for (r in n - 1 downTo 0) {
            if (leftToRight) {
                for (c in 0 until n) flat[k++] = board[r]!![c]
            } else {
                for (c in n - 1 downTo 0) flat[k++] = board[r]!![c]
            }
            leftToRight = !leftToRight
        }

        val memo = IntArray(flat.size) { Int.MAX_VALUE }
        val visiting = HashSet<Int>()

        fun dfs(pos: Int): Int {
            if (pos == targetIdx) return 0
            if (pos > targetIdx) return Int.MAX_VALUE
            if (!visiting.add(pos)) return Int.MAX_VALUE   // cycle guard
            if (memo[pos] != Int.MAX_VALUE) {
                visiting.remove(pos)
                return memo[pos]
            }

            var best = Int.MAX_VALUE
            for (d in 1..6) {
                val nxt = pos + d
                if (nxt > targetIdx) break
                val jump = if (flat[nxt] != -1) flat[nxt] - 1 else nxt
                val tail = dfs(jump)
                if (tail != Int.MAX_VALUE) {
                    best = minOf(best, 1 + tail)
                    if (best == 1) break
                }
            }
            visiting.remove(pos)
            memo[pos] = best
            return best
        }

        val ans = dfs(0)
        return if (ans == Int.MAX_VALUE) -1 else ans
    }
}