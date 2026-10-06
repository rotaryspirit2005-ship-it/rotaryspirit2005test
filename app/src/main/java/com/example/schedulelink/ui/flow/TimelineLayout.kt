package com.example.schedulelink.ui.flow

import androidx.compose.ui.geometry.Offset
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// タイムライン(全体マップ)の配置と配線。単位はすべてdp。画面に依存しない純粋な計算なので、
// 重い配線の探索はViewModel側でバックグラウンドに回している。

const val PX_PER_DAY = 8f
/** 題名が省略されにくいよう広めにとる(WholeTreeLayoutのNODE_WIDTH_DAYSと対応させる)。 */
const val NODE_WIDTH = 144f
const val NODE_HEIGHT = 52f
/** カードを縦に重ねるレーンの間隔。ここにも線が通れるよう、線の間隔(CELL)の数本分をあける。 */
const val LANE_GAP = 24f
const val LANE_HEIGHT = NODE_HEIGHT + LANE_GAP
const val RULER_HEIGHT = 48f
const val MARGIN = 24f

/** 配線に使う格子の1マスの大きさ。線どうしの最小の間隔になる。 */
private const val CELL = 6f
/** カードのまわりに、線が近づけない余白(マス数)。 */
private const val CARD_MARGIN_CELLS = 1
private const val TOP_GAP = 60f

/** 格子が大きすぎる(期間がとても長い)ときは、探索せず簡易な線にする。 */
private const val MAX_SEARCH_STATES = 2_500_000
/** 1本の線の探索で調べるマスの上限。超えたら簡易な線にする。 */
private const val MAX_EXPANSIONS = 150_000

fun timelineX(minDate: LocalDate, date: LocalDate): Float =
    MARGIN + ChronoUnit.DAYS.between(minDate, date) * PX_PER_DAY

class NodeBox(val left: Float, val top: Float) {
    val right: Float get() = left + NODE_WIDTH
    val bottom: Float get() = top + NODE_HEIGHT
    val centerX: Float get() = left + NODE_WIDTH / 2
    val centerY: Float get() = top + NODE_HEIGHT / 2
}

enum class RouteKind { TREE, PEER }

class TimelineRoute(
    val fromId: String,
    val toId: String,
    val kind: RouteKind,
    /** 木の線のとき、親の階層(線の色の決定に使う)。 */
    val parentTier: TreeTier,
    val points: List<Offset>
)

class TimelineLayout(
    val boxes: Map<String, NodeBox>,
    val routes: List<TimelineRoute>,
    val width: Float,
    val height: Float
)

/** [tree]と、そこから計算した配置・配線。 */
class TimelineState(val tree: WholeTree, val layout: TimelineLayout)

/**
 * 日付に沿ってカードを配置し、親子の線・予定どうしのリンクを、カードを避けて直角に曲がる
 * 線(Simulinkのような配線)で結ぶ。別々の線どうしは、できるだけ同じ場所を通らない。
 */
fun layoutTimeline(tree: WholeTree): TimelineLayout {
    if (tree.nodes.isEmpty()) return TimelineLayout(emptyMap(), emptyList(), 0f, 0f)

    val nodesById = tree.nodes.associateBy { it.id }
    val maxLane = TreeTier.entries.associateWith { tier ->
        tree.nodes.filter { it.tier == tier }.maxOfOrNull { it.lane } ?: -1
    }
    val parentCount = TreeTier.entries.associateWith { tier ->
        tree.treeEdges.filter { nodesById[it.first]?.tier == tier }.map { it.first }.distinct().size
    }
    // 親の階層の下の隙間は、そこから出る線の本数に応じて広げる(線が通る道を確保する)。
    fun gapBelow(tier: TreeTier): Float = (48f + 14f * (parentCount[tier] ?: 0)).coerceIn(72f, 220f)

    val baseY = HashMap<TreeTier, Float>()
    var y = RULER_HEIGHT + TOP_GAP
    for (tier in TreeTier.entries) {
        baseY[tier] = y
        y += LANE_HEIGHT * ((maxLane.getValue(tier) + 1).coerceAtLeast(0)) + gapBelow(tier)
    }
    val height = y
    val width = timelineX(tree.minDate, tree.maxDate) + NODE_WIDTH + MARGIN

    val boxes = tree.nodes.associate { node ->
        node.id to NodeBox(
            left = timelineX(tree.minDate, node.date),
            top = baseY.getValue(node.tier) + LANE_HEIGHT * node.lane
        )
    }

    class Spec(
        val fromId: String,
        val toId: String,
        val kind: RouteKind,
        val parentTier: TreeTier,
        val net: String,
        val sources: List<Pair<Side, Float>>,
        val targets: List<Pair<Side, Float>>
    )

    val specs = mutableListOf<Spec>()
    for ((parentId, childId) in tree.treeEdges) {
        val parent = nodesById[parentId] ?: continue
        if (nodesById[childId] == null) continue
        // 親(上)の下から出て、子の左・上・右のどこかから入る。同じ親の線は途中で1本にまとまる。
        specs += Spec(
            parentId, childId, RouteKind.TREE, parent.tier, net = "tree:$parentId",
            sources = listOf(Side.BOTTOM to 0f),
            targets = listOf(Side.LEFT to 0f, Side.TOP to 0f, Side.RIGHT to 3f)
        )
    }
    for (link in tree.peerLinks) {
        val a = nodesById[link.fromId] ?: continue
        val b = nodesById[link.toId] ?: continue
        // 日付の早い方(同じ日ならレーンが上の方)を始点にする。
        val aFirst = a.date < b.date || (a.date == b.date && a.lane <= b.lane)
        val (first, second) = if (aFirst) a to b else b to a
        specs += Spec(
            first.id, second.id, RouteKind.PEER, first.tier, net = "peer:${first.id}|${second.id}",
            sources = listOf(Side.RIGHT to 0f, Side.BOTTOM to 1f),
            targets = listOf(Side.LEFT to 0f, Side.TOP to 1f, Side.BOTTOM to 2f, Side.RIGHT to 2f)
        )
    }

    // 近い線から先に敷く(遠回りになる長い線は、後から空いた道を探す)。
    specs.sortBy { spec ->
        val a = boxes.getValue(spec.fromId)
        val b = boxes.getValue(spec.toId)
        abs(a.centerX - b.centerX) + abs(a.centerY - b.centerY)
    }

    // 格子が大きすぎるときは、作業領域を確保する前にあきらめる(簡易な線になる)。
    val searchStates = (ceil(width / CELL).toLong() + 2) * (ceil(height / CELL).toLong() + 2) * 5
    val router = if (searchStates <= MAX_SEARCH_STATES) GridRouter(width, height, boxes) else null
    val netIds = HashMap<String, Int>()
    val routes = specs.map { spec ->
        val net = netIds.getOrPut(spec.net) { netIds.size }
        val points = router?.route(spec.fromId, spec.toId, spec.sources, spec.targets, net)
            ?: fallbackRoute(boxes.getValue(spec.fromId), boxes.getValue(spec.toId))
        TimelineRoute(spec.fromId, spec.toId, spec.kind, spec.parentTier, points)
    }
    return TimelineLayout(boxes, routes, width, height)
}

/** 配線を探索できなかったときの簡易な線(カードを避けないが、必ず結ぶ)。 */
private fun fallbackRoute(from: NodeBox, to: NodeBox): List<Offset> {
    val midY = (from.bottom + to.top) / 2
    return listOf(
        Offset(from.centerX, from.bottom),
        Offset(from.centerX, midY),
        Offset(to.centerX, midY),
        Offset(to.centerX, to.top)
    )
}

private enum class Side { TOP, BOTTOM, LEFT, RIGHT }

/** カードが占める格子のマス(余白を含む)。 */
private class CellBox(val c0: Int, val r0: Int, val c1: Int, val r1: Int)

private class Port(val col: Int, val row: Int, val cost: Float, val side: Side)

/**
 * 格子の上で、直角にしか曲がらない線をA*で探す。カードは通らず、曲がる回数は少なく、
 * 別の線(別のnet)と同じ向きで重なる道は避ける。同じ親から出る線(同じnet)は
 * 先に敷いた線に沿うと安くなるので、途中で1本にまとまる。
 */
private class GridRouter(width: Float, height: Float, boxes: Map<String, NodeBox>) {
    private val cols = ceil(width / CELL).toInt() + 2
    private val rows = ceil(height / CELL).toInt() + 2
    val stateCount: Int = cols * rows * 5

    private val blocked = BooleanArray(cols * rows)
    private val occH = IntArray(cols * rows) { -1 }
    private val occV = IntArray(cols * rows) { -1 }
    private val cellBoxes = HashMap<String, CellBox>()
    private val boxesById = boxes

    // 探索の作業領域(1本ごとに作り直さず、stampで「今回の探索の値か」を見分ける)。
    private val best = FloatArray(stateCount)
    private val parent = IntArray(stateCount)
    private val stamp = IntArray(stateCount)
    private var currentStamp = 0

    init {
        for ((id, box) in boxes) {
            val c0 = floor(box.left / CELL).toInt() - CARD_MARGIN_CELLS
            val c1 = ceil(box.right / CELL).toInt() - 1 + CARD_MARGIN_CELLS
            val r0 = floor(box.top / CELL).toInt() - CARD_MARGIN_CELLS
            val r1 = ceil(box.bottom / CELL).toInt() - 1 + CARD_MARGIN_CELLS
            cellBoxes[id] = CellBox(c0, r0, c1, r1)
            for (r in r0.coerceAtLeast(0)..r1.coerceAtMost(rows - 1)) {
                for (c in c0.coerceAtLeast(0)..c1.coerceAtMost(cols - 1)) {
                    blocked[r * cols + c] = true
                }
            }
        }
    }

    private fun portOf(id: String, side: Side, cost: Float): Port? {
        val cb = cellBoxes[id] ?: return null
        val cm = (cb.c0 + cb.c1) / 2
        val rm = (cb.r0 + cb.r1) / 2
        val (c, r) = when (side) {
            Side.BOTTOM -> cm to cb.r1 + 1
            Side.TOP -> cm to cb.r0 - 1
            Side.LEFT -> cb.c0 - 1 to rm
            Side.RIGHT -> cb.c1 + 1 to rm
        }
        if (c !in 0 until cols || r !in 0 until rows) return null
        return Port(c, r, cost, side)
    }

    private fun centerOf(col: Int, row: Int) = Offset(col * CELL + CELL / 2, row * CELL + CELL / 2)

    /** カードの縁の、線がつながる点(ポートの格子の中心と、縦か横にそろう)。 */
    private fun anchorOf(id: String, port: Port): Offset {
        val box = boxesById.getValue(id)
        val center = centerOf(port.col, port.row)
        return when (port.side) {
            Side.BOTTOM -> Offset(center.x, box.bottom)
            Side.TOP -> Offset(center.x, box.top)
            Side.LEFT -> Offset(box.left, center.y)
            Side.RIGHT -> Offset(box.right, center.y)
        }
    }

    /**
     * 線の端をカードの中心まで延ばす点。カードは拡大すると縮小表示される(縁が内側へ動く)ので、
     * 縁で止めると線が離れてしまう。カードは不透明で上に重なるので、延ばした分は隠れる。
     */
    private fun innerOf(id: String, port: Port): Offset {
        val box = boxesById.getValue(id)
        val center = centerOf(port.col, port.row)
        return when (port.side) {
            Side.BOTTOM, Side.TOP -> Offset(center.x, box.centerY)
            Side.LEFT, Side.RIGHT -> Offset(box.centerX, center.y)
        }
    }

    fun route(
        fromId: String,
        toId: String,
        sources: List<Pair<Side, Float>>,
        targets: List<Pair<Side, Float>>,
        net: Int
    ): List<Offset>? {
        val starts = sources.mapNotNull { (side, cost) -> portOf(fromId, side, cost) }
        val goals = targets.mapNotNull { (side, cost) -> portOf(toId, side, cost) }
        if (starts.isEmpty() || goals.isEmpty()) return null

        fun heuristic(col: Int, row: Int): Float {
            var m = Int.MAX_VALUE
            for (g in goals) m = minOf(m, abs(col - g.col) + abs(row - g.row))
            return 0.5f * m
        }

        currentStamp++
        val heap = LongHeap()
        for (s in starts) {
            val st = (s.row * cols + s.col) * 5 + 4
            if (stamp[st] != currentStamp || s.cost < best[st]) {
                stamp[st] = currentStamp
                best[st] = s.cost
                parent[st] = -1
                heap.push(pack(s.cost + heuristic(s.col, s.row), st))
            }
        }

        var foundState = -1
        var expansions = 0
        while (!heap.isEmpty()) {
            val packed = heap.pop()
            val st = (packed and 0xFFFFFFFFL).toInt()
            val f = Float.fromBits((packed ushr 32).toInt())
            val dir = st % 5
            val cell = st / 5
            val col = cell % cols
            val row = cell / cols
            val g = best[st]
            if (f - heuristic(col, row) > g * 1.00002f + 1e-3f) continue // 後からもっと安い道が見つかった古い項目
            if (goals.any { it.col == col && it.row == row }) {
                foundState = st
                break
            }
            if (++expansions > MAX_EXPANSIONS) break
            for (nd in 0 until 4) {
                val nc = col + DC[nd]
                val nr = row + DR[nd]
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
                val ncell = nr * cols + nc
                val goal = goals.firstOrNull { it.col == nc && it.row == nr }
                if (blocked[ncell] && goal == null) continue
                val horizontal = nd < 2
                val own = if (horizontal) occH[ncell] else occV[ncell]
                val cross = if (horizontal) occV[ncell] else occH[ncell]
                var step = 1f
                if (own != -1) {
                    step = if (own == net) 0.5f else 25f
                } else if (cross != -1 && cross != net) {
                    step += 4f
                }
                if (dir != 4 && dir != nd) step += 3f
                if (goal != null) step += goal.cost
                val ncost = g + step
                val nst = ncell * 5 + nd
                if (stamp[nst] != currentStamp || ncost < best[nst] - 1e-3f) {
                    stamp[nst] = currentStamp
                    best[nst] = ncost
                    parent[nst] = st
                    heap.push(pack(ncost + heuristic(nc, nr), nst))
                }
            }
        }
        if (foundState == -1) return null

        val cells = ArrayList<Int>()
        var s = foundState
        while (s != -1) {
            cells.add(s / 5)
            s = parent[s]
        }
        cells.reverse()

        // 通った道を「使用中」にして、後から敷く別の線がよけられるようにする。
        for (i in 1 until cells.size) {
            val prev = cells[i - 1]
            val cur = cells[i]
            val occ = if (prev / cols == cur / cols) occH else occV
            if (occ[prev] == -1) occ[prev] = net
            if (occ[cur] == -1) occ[cur] = net
        }

        val startPort = starts.first { it.row * cols + it.col == cells.first() }
        val goalPort = goals.first { it.row * cols + it.col == cells.last() }
        val points = ArrayList<Offset>(cells.size + 2)
        points.add(innerOf(fromId, startPort))
        points.add(anchorOf(fromId, startPort))
        for (cell in cells) points.add(centerOf(cell % cols, cell / cols))
        points.add(anchorOf(toId, goalPort))
        points.add(innerOf(toId, goalPort))
        return simplify(points)
    }

    private fun pack(f: Float, state: Int): Long = (f.toRawBits().toLong() shl 32) or state.toLong()

    /** 同じ向きに続く点(途中の点)を省いて、曲がり角だけの折れ線にする。 */
    private fun simplify(points: List<Offset>): List<Offset> {
        val result = ArrayList<Offset>(points.size)
        for (p in points) {
            if (result.isNotEmpty() && result.last() == p) continue
            while (result.size >= 2) {
                val a = result[result.size - 2]
                val b = result[result.size - 1]
                val collinear = (a.x == b.x && b.x == p.x) || (a.y == b.y && b.y == p.y)
                if (collinear) result.removeAt(result.size - 1) else break
            }
            result.add(p)
        }
        return result
    }

    private companion object {
        val DC = intArrayOf(1, -1, 0, 0)
        val DR = intArrayOf(0, 0, 1, -1)
    }
}

/** Long(上位に優先度、下位に状態番号)を入れる最小ヒープ。ボックス化を避けるため自前で持つ。 */
private class LongHeap {
    private var data = LongArray(1024)
    private var size = 0

    fun isEmpty(): Boolean = size == 0

    fun push(value: Long) {
        if (size == data.size) data = data.copyOf(size * 2)
        var i = size++
        while (i > 0) {
            val p = (i - 1) / 2
            if (data[p] <= value) break
            data[i] = data[p]
            i = p
        }
        data[i] = value
    }

    fun pop(): Long {
        val top = data[0]
        val last = data[--size]
        if (size > 0) {
            var i = 0
            while (true) {
                var c = 2 * i + 1
                if (c >= size) break
                if (c + 1 < size && data[c + 1] < data[c]) c++
                if (data[c] >= last) break
                data[i] = data[c]
                i = c
            }
            data[i] = last
        }
        return top
    }
}
