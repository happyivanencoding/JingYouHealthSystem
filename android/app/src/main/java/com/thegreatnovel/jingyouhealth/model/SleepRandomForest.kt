package com.thegreatnovel.jingyouhealth.model

import java.util.Random
import kotlin.math.sqrt

/** Small, deterministic bootstrap CART forest for local, bounded personal-history exploration. */
internal class SleepRandomForest private constructor(private val trees: List<Node>) {
    fun predict(features: DoubleArray): Double = trees.sumOf { tree ->
        var node = tree
        while (node.feature >= 0) node = if (features[node.feature] <= node.threshold) node.left!! else node.right!!
        node.value
    } / trees.size

    private data class Node(val value: Double, val feature: Int = -1, val threshold: Double = 0.0, val left: Node? = null, val right: Node? = null)

    companion object {
        const val TREE_COUNT = 32
        const val MAX_DEPTH = 3
        const val MIN_LEAF = 8
        fun fit(x: List<DoubleArray>, y: DoubleArray, seed: Long = 29173L): SleepRandomForest {
            require(x.isNotEmpty() && x.size == y.size && x.first().isNotEmpty())
            val dimensions = x.first().size
            require(x.all { it.size == dimensions && it.all(Double::isFinite) } && y.all(Double::isFinite))
            val random = Random(seed)
            val mtry = sqrt(dimensions.toDouble()).toInt().coerceAtLeast(1)
            fun build(rows: IntArray, depth: Int): Node {
                val sum = rows.sumOf { y[it] }
                val squareSum = rows.sumOf { y[it] * y[it] }
                val mean = sum / rows.size
                if (depth >= MAX_DEPTH || rows.size < MIN_LEAF * 2) return Node(mean)
                val candidates = (0 until dimensions).toMutableList()
                for (i in candidates.lastIndex downTo 1) {
                    val j = random.nextInt(i + 1)
                    val old = candidates[i]; candidates[i] = candidates[j]; candidates[j] = old
                }
                var bestLoss = squareSum - sum * sum / rows.size
                var bestFeature = -1
                var threshold = 0.0
                for (feature in candidates.take(mtry)) {
                    val ordered = rows.sortedBy { x[it][feature] }
                    var leftSum = 0.0
                    var leftSquares = 0.0
                    for (i in 0 until ordered.lastIndex) {
                        val value = y[ordered[i]]
                        leftSum += value; leftSquares += value * value
                        val leftCount = i + 1
                        val rightCount = ordered.size - leftCount
                        if (leftCount < MIN_LEAF || rightCount < MIN_LEAF) continue
                        val lo = x[ordered[i]][feature]
                        val hi = x[ordered[i + 1]][feature]
                        if (lo == hi) continue
                        val rightSum = sum - leftSum
                        val loss = leftSquares - leftSum * leftSum / leftCount + squareSum - leftSquares - rightSum * rightSum / rightCount
                        if (loss < bestLoss - 1e-10) {
                            bestLoss = loss; bestFeature = feature; threshold = lo + (hi - lo) / 2.0
                        }
                    }
                }
                if (bestFeature < 0) return Node(mean)
                val left = rows.filter { x[it][bestFeature] <= threshold }.toIntArray()
                val right = rows.filter { x[it][bestFeature] > threshold }.toIntArray()
                if (left.size < MIN_LEAF || right.size < MIN_LEAF) return Node(mean)
                return Node(mean, bestFeature, threshold, build(left, depth + 1), build(right, depth + 1))
            }
            return SleepRandomForest(List(TREE_COUNT) { build(IntArray(x.size) { random.nextInt(x.size) }, 0) })
        }
    }
}
