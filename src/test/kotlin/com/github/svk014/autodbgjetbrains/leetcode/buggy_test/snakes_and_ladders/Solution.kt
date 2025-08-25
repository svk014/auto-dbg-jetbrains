package com.github.svk014.autodbgjetbrains.leetcode.buggy_test.snakes_and_ladders

import kotlin.math.min

class Solution {
    private var lastCell = 0
    private var memo: MutableMap<Int?, Int?>? = null

    fun snakesAndLadders(board: Array<IntArray?>): Int {
        memo = HashMap()

        val n = board.size
        val flatBoard = IntArray(n * n)
        this.lastCell = n * n - 1

        var index = 0
        var reverseRow = false
        for (i in n - 1 downTo 0) {
            if (reverseRow) {
                for (j in n - 1 downTo 0) {
                    flatBoard[index++] = board[i]!![j]
                }
            } else {
                for (j in 0..<n) {
                    flatBoard[index++] = board[i]!![j]
                }
            }
            reverseRow = !reverseRow
        }

        val result = dfs(0, flatBoard, HashSet())
        return if (result == Int.Companion.MAX_VALUE) -1 else result
    }

    private fun dfs(cell: Int, flatBoard: IntArray, visited: MutableSet<Int?>): Int {
        if (visited.contains(cell)) {
            return Int.Companion.MAX_VALUE
        }

        if (memo!!.containsKey(cell)) {
            return memo!![cell]!!
        }

        if (cell == lastCell) {
            return 0
        }

        if (cell > lastCell) {
            return Int.Companion.MAX_VALUE
        }

        visited.add(cell)

        var minMoves = Int.Companion.MAX_VALUE
        for (dice in 1..6) {
            val nextCell = cell + dice

            if (nextCell > lastCell) {
                continue
            }

            if (flatBoard[nextCell] != -1) {
                val destination = flatBoard[nextCell] - 1
                val movesFromDest = dfs(destination, flatBoard, visited)
                if (movesFromDest != Int.Companion.MAX_VALUE) {
                    minMoves = min(minMoves, 1 + movesFromDest)
                }
            } else {
                val movesFromNext = dfs(nextCell, flatBoard, visited)
                if (movesFromNext != Int.Companion.MAX_VALUE) {
                    minMoves = min(minMoves, 1 + movesFromNext)
                }
            }
        }

        visited.remove(cell)
        memo!!.put(cell, minMoves)

        return minMoves
    }
}