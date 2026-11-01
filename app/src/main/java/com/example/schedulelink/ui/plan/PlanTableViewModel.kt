package com.example.schedulelink.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** 登録前の確認で使う、1行ぶんの解決結果。 */
data class ResolvedRow(
    val row: PlanRow,
    /** 日付が空のとき、子や親から補った後の「いつ」。 */
    val start: LocalDate?,
    val end: LocalDate?,
    /** 同じ名前で登録済みの大目的・中日程があれば、そのID(新しく作らず、そこに追加する)。 */
    val matchedId: String?,
    /** 登録できない理由。nullなら登録できる。 */
    val error: String?,
    /** 補足(日付を補ったなど)。 */
    val note: String?
)

/**
 * 行の並びを、登録できる形に解決する。日付が空の行は子(中日程なら予定、大目的なら中日程と予定)の
 * 範囲から補い、予定の日付が空なら中日程の開始日にする。同じ名前の大目的・中日程があれば追加先にする。
 */
fun resolvePlan(
    rows: List<PlanRow>,
    existingGoals: List<GoalEntity>,
    existingMilestones: List<MilestoneEntity>
): List<ResolvedRow> {
    val parents = parentsOf(rows)
    fun childrenOf(parent: PlanRow): List<PlanRow> = rows.filter { parents[it.id]?.id == parent.id }
    fun descendants(parent: PlanRow): List<PlanRow> = childrenOf(parent).flatMap { listOf(it) + descendants(it) }

    val goalMatch = mutableMapOf<String, String?>()
    rows.filter { it.level == PlanLevel.Why }.forEach { row ->
        goalMatch[row.id] = row.existingId ?: existingGoals.firstOrNull { it.title.trim() == row.title.trim() }?.id
    }
    val milestoneMatch = mutableMapOf<String, MilestoneEntity?>()
    rows.filter { it.level == PlanLevel.How }.forEach { row ->
        val goalId = parents[row.id]?.let { goalMatch[it.id] }
        milestoneMatch[row.id] = existingMilestones.firstOrNull { it.id == row.existingId }
            ?: goalId?.let { id -> existingMilestones.firstOrNull { it.goalId == id && it.title.trim() == row.title.trim() } }
    }

    fun rangeOf(row: PlanRow): Pair<LocalDate?, LocalDate?> {
        val own = row.start to (row.end ?: row.start)
        if (row.level == PlanLevel.What || (own.first != null && own.second != null)) return own
        val dates = descendants(row).flatMap { listOfNotNull(it.start, it.end) }
        return (row.start ?: dates.minOrNull()) to (row.end ?: dates.maxOrNull() ?: row.start)
    }

    return rows.map { row ->
        val parent = parents[row.id]
        val parentMissing = row.level != PlanLevel.Why && parent == null
        var error = row.issue(parentMissing)
        var note: String? = null
        var (start, end) = rangeOf(row)
        val matchedId = when (row.level) {
            PlanLevel.Why -> goalMatch[row.id]
            PlanLevel.How -> row.existingId ?: milestoneMatch[row.id]?.id
            PlanLevel.What -> null
        }
        if (error == null && matchedId == null) {
            when (row.level) {
                PlanLevel.What -> if (start == null) {
                    // 登録済みの中日程の下なら、その中日程の開始日を使う
                    val parentStart = parent?.let { milestoneMatch[it.id]?.startDate ?: rangeOf(it).first }
                    if (parentStart == null) {
                        error = "「いつ」を選んでください"
                    } else {
                        start = parentStart
                        end = parentStart
                        note = "日付が空なので、中日程の開始日にします"
                    }
                }
                else -> when {
                    start == null || end == null -> error = "「いつ」を選んでください(下の予定に日付があれば自動で決まります)"
                    row.start == null || row.end == null -> note = "期間は下の行の日付から決めます"
                }
            }
        }
        ResolvedRow(row, start, end, matchedId, error, note)
    }
}

class PlanTableViewModel(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _rows = MutableStateFlow<List<PlanRow>>(emptyList())
    val rows: StateFlow<List<PlanRow>> = _rows.asStateFlow()

    private var initialized = false
    private var store: PlanDraftStore? = null

    /** 行が変わるたびに端末へ残す。登録の途中で画面を離れても、登録済みの印が失われないようにする。 */
    private fun setRows(rows: List<PlanRow>) {
        _rows.value = rows
        store?.let { if (rows.isEmpty()) it.clear() else it.save(rows) }
    }

    /** 画面を開いたときに1度だけ、下書きと(大目的の画面から来たときは)その大目的を読み込む。 */
    fun initialize(store: PlanDraftStore, goalId: String?) {
        if (initialized) return
        initialized = true
        this.store = store
        val saved = store.load()
        _rows.value = saved
        if (goalId != null && saved.none { it.existingId == goalId }) {
            viewModelScope.launch {
                val goal = try {
                    goalRepository.allGoalsOnce().firstOrNull { it.id == goalId }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (goal == null) {
                    if (_rows.value.isEmpty()) setRows(listOf(PlanRow(level = PlanLevel.Why)))
                    return@launch
                }
                val goalRow = PlanRow(level = PlanLevel.Why, title = goal.title, existingId = goal.id, tagIds = goal.tagIds)
                setRows(_rows.value + goalRow + PlanRow(level = PlanLevel.How))
            }
        } else if (saved.isEmpty()) {
            setRows(listOf(PlanRow(level = PlanLevel.Why)))
        }
    }

    fun update(row: PlanRow) {
        setRows(_rows.value.map { if (it.id == row.id) row else it })
    }

    /** [afterId]の行のすぐ下に、[level]の行を足す。足した行のIDを返す(入力欄を移すため)。 */
    fun insertAfter(afterId: String, level: PlanLevel): String {
        val list = _rows.value.toMutableList()
        val index = list.indexOfFirst { it.id == afterId }
        // 行の下に子の行が続いているときは、子の後ろに足す(同じ段の次の行として)
        var insertAt = index + 1
        if (index >= 0) {
            while (insertAt < list.size && list[insertAt].level.depth > level.depth) insertAt++
        }
        insertAt = insertAt.coerceIn(0, list.size)
        val previous = list.getOrNull(insertAt - 1)
        // 予定を続けて書くときは、すぐ上の予定の翌日を先に入れておく(違えば直すだけで済む)
        val start = if (level == PlanLevel.What && previous?.level == PlanLevel.What) previous.start?.plusDays(1) else null
        val row = PlanRow(
            level = level,
            start = start,
            tagIds = if (level == PlanLevel.What) list.getOrNull(index)?.tagIds.orEmpty() else emptyList()
        )
        list.add(insertAt, row)
        setRows(list)
        return row.id
    }

    /**
     * タイトル欄のひと言入力を読み取り、空いている欄にだけ入れる(手で決めた値は上書きしない)。
     * 入れなかった語はタイトルに残す。
     */
    fun applyShorthand(id: String, tags: List<ShorthandTag>) {
        val row = _rows.value.firstOrNull { it.id == id } ?: return
        if (row.existingId != null) return
        val parsed = parseShorthand(row.title, tags)
        if (!parsed.foundAnything) return
        var next = row.copy(title = parsed.title)
        val leftover = mutableListOf<String>()
        val tokens = row.autoTokens.toMutableMap()
        if (parsed.start != null && parsed.dateText != null) {
            if (row.start == null) {
                next = next.copy(start = parsed.start, end = if (row.level == PlanLevel.What) null else parsed.end ?: parsed.start)
                tokens["when"] = parsed.dateText
            } else {
                leftover += parsed.dateText
            }
        }
        if (parsed.location != null && parsed.locationText != null) {
            if (row.level == PlanLevel.What && row.location.isBlank()) {
                next = next.copy(location = parsed.location)
                tokens["where"] = parsed.locationText
            } else {
                leftover += parsed.locationText
            }
        }
        if (parsed.tagIds.isNotEmpty() && parsed.tagText != null) {
            if (row.tagIds.isEmpty()) {
                next = next.copy(tagIds = parsed.tagIds)
                tokens["who"] = parsed.tagText
            } else {
                leftover += parsed.tagText
            }
        }
        next = next.copy(
            title = (listOf(next.title) + leftover).filter { it.isNotBlank() }.joinToString(" "),
            autoTokens = tokens
        )
        if (next != row) update(next)
    }

    /** ひと言入力で入れた欄を消し、元の語をタイトルに戻す。 */
    fun undoShorthand(id: String, field: String) {
        val row = _rows.value.firstOrNull { it.id == id } ?: return
        val token = row.autoTokens[field] ?: return
        val cleared = when (field) {
            "when" -> row.copy(start = null, end = null)
            "who" -> row.copy(tagIds = emptyList())
            else -> row.copy(location = "")
        }
        update(
            cleared.copy(
                title = listOf(row.title, token).filter { it.isNotBlank() }.joinToString(" "),
                autoTokens = row.autoTokens - field
            )
        )
    }

    /** ひな形などの行をまとめて足す。表が空の1行だけなら、置き換える。 */
    fun appendRows(newRows: List<PlanRow>) {
        val current = _rows.value
        val blank = current.size == 1 && current[0].existingId == null && current[0].title.isBlank()
        setRows(if (blank || current.isEmpty()) newRows else current + newRows)
    }

    fun append(level: PlanLevel): String {
        val row = PlanRow(level = level)
        setRows(_rows.value + row)
        return row.id
    }

    fun delete(id: String): Pair<Int, PlanRow>? {
        val index = _rows.value.indexOfFirst { it.id == id }
        if (index < 0) return null
        val row = _rows.value[index]
        setRows(_rows.value.filterNot { it.id == id })
        return index to row
    }

    fun restore(index: Int, row: PlanRow) {
        val list = _rows.value.toMutableList()
        list.add(index.coerceIn(0, list.size), row)
        setRows(list)
    }

    fun move(id: String, delta: Int) {
        val list = _rows.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.add(target, list.removeAt(index))
        setRows(list)
    }

    /** 字下げを1段深く(+1)・浅く(-1)する。登録済みの行は段を変えない。 */
    fun indent(id: String, delta: Int) {
        setRows(
            _rows.value.map {
                if (it.id == id && it.existingId == null) it.copy(level = PlanLevel.ofDepth(it.level.depth + delta)) else it
            }
        )
    }

    fun clear() {
        setRows(listOf(PlanRow(level = PlanLevel.Why)))
    }

    // ---- AIで分解 ----

    /** 分解の状態。提案は確認画面にも下書きにも入れず、受け入れたものだけを行にする。 */
    sealed interface AiState {
        data object Loading : AiState
        data class Failed(val message: String) : AiState
        data class Ready(val groups: List<SuggestionGroup>) : AiState
    }

    private val _ai = MutableStateFlow<Map<String, AiState>>(emptyMap())
    val ai: StateFlow<Map<String, AiState>> = _ai.asStateFlow()
    private val aiJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun requestBreakdown(anchorId: String, apiKey: String) {
        val rows = _rows.value
        val anchor = rows.firstOrNull { it.id == anchorId } ?: return
        val parents = parentsOf(rows)
        val resolved = resolvePlan(rows, emptyList(), emptyList()).firstOrNull { it.row.id == anchorId }
        val children = rows.filter { parents[it.id]?.id == anchorId }.map { it.title }.filter { it.isNotBlank() }
        aiJobs[anchorId]?.cancel()
        _ai.value = _ai.value + (anchorId to AiState.Loading)
        aiJobs[anchorId] = viewModelScope.launch {
            val state = try {
                AiState.Ready(
                    breakDownWithClaude(
                        apiKey = apiKey,
                        anchor = anchor,
                        anchorStart = resolved?.start,
                        anchorEnd = resolved?.end,
                        context = parents[anchorId]?.title,
                        existingChildren = children
                    )
                )
            } catch (e: PlanAiException) {
                AiState.Failed(e.message.orEmpty())
            }
            _ai.value = _ai.value + (anchorId to state)
        }
    }

    fun cancelBreakdown(anchorId: String) {
        aiJobs.remove(anchorId)?.cancel()
        _ai.value = _ai.value - anchorId
    }

    /** 提案のまとまりを受け入れ、元の行の下(その行の子の最後)に足す。 */
    fun acceptSuggestion(anchorId: String, group: SuggestionGroup) {
        val list = _rows.value.toMutableList()
        val index = list.indexOfFirst { it.id == anchorId }
        if (index < 0) return
        var insertAt = index + 1
        while (insertAt < list.size && list[insertAt].level.depth > list[index].level.depth) insertAt++
        list.addAll(insertAt, group.rows)
        setRows(list)
        dropSuggestion(anchorId, group)
    }

    fun dropSuggestion(anchorId: String, group: SuggestionGroup) {
        val state = _ai.value[anchorId] as? AiState.Ready ?: return
        val left = state.groups - group
        _ai.value = if (left.isEmpty()) _ai.value - anchorId else _ai.value + (anchorId to AiState.Ready(left))
    }

    fun acceptAll(anchorId: String) {
        val state = _ai.value[anchorId] as? AiState.Ready ?: return
        state.groups.forEach { acceptSuggestion(anchorId, it) }
    }

    // ---- 確認と登録 ----

    private val _existing = MutableStateFlow<Pair<List<GoalEntity>, List<MilestoneEntity>>?>(null)
    /** 名前の照合に使う、登録済みの大目的と中日程(確認画面を開いたときに読む)。 */
    val existing: StateFlow<Pair<List<GoalEntity>, List<MilestoneEntity>>?> = _existing.asStateFlow()

    fun loadExisting(onError: () -> Unit) {
        _existing.value = null
        viewModelScope.launch {
            try {
                val goals = async { goalRepository.allGoalsOnce() }
                val milestones = async { milestoneRepository.allMilestonesOnce() }
                _existing.value = goals.await() to milestones.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError()
            }
        }
    }

    /**
     * チェックした行を順に登録する(大目的→中日程→予定)。途中で失敗しても、登録できた分は
     * 下書きに「登録済み」として残し(大目的・中日程)、または取り除く(予定)ので、やり直しても二重にならない。
     */
    fun register(
        included: Set<String>,
        onDone: (createdCount: Int, goalIds: List<String>) -> Unit,
        onError: (createdCount: Int) -> Unit
    ) {
        val (goals, milestones) = _existing.value ?: return
        viewModelScope.launch {
            val resolved = resolvePlan(_rows.value, goals, milestones)
            val parents = parentsOf(_rows.value)
            val ids = mutableMapOf<String, String>()
            val goalIds = mutableListOf<String>()
            var created = 0
            // 既存の並び順の最大値の次から付ける(番号が飛んでいても重ならないように)
            val nextOrder = milestones.groupBy { it.goalId }
                .mapValues { (_, list) -> list.maxOf { it.orderIndex } + 1 }
                .toMutableMap()
            try {
                resolved.forEach { r ->
                    val row = r.row
                    if (row.id !in included || r.error != null) return@forEach
                    val parentId = parents[row.id]?.let { ids[it.id] }
                    when (row.level) {
                        PlanLevel.Why -> {
                            val id = r.matchedId ?: goalRepository.saveGoal(
                                GoalEntity(
                                    title = row.title.trim(),
                                    type = GoalType.PHASED,
                                    startDate = r.start,
                                    endDate = r.end,
                                    tagIds = row.tagIds
                                )
                            ).also { created++ }
                            ids[row.id] = id
                            goalIds += id
                            markExisting(row.id, id)
                        }
                        PlanLevel.How -> {
                            if (parentId == null) return@forEach
                            val order = nextOrder[parentId] ?: 0
                            val id = r.matchedId ?: milestoneRepository.saveMilestone(
                                MilestoneEntity(
                                    goalId = parentId,
                                    title = row.title.trim(),
                                    startDate = r.start,
                                    endDate = r.end,
                                    orderIndex = order,
                                    tagIds = row.tagIds
                                )
                            ).also {
                                created++
                                nextOrder[parentId] = order + 1
                            }
                            ids[row.id] = id
                            markExisting(row.id, id)
                        }
                        PlanLevel.What -> {
                            if (parentId == null) return@forEach
                            val date = r.start ?: return@forEach
                            // 誰がを空にした予定は、中日程の「誰が」を引き継ぐ
                            val tags = row.tagIds.ifEmpty { parents[row.id]?.tagIds.orEmpty() }
                            scheduleRepository.saveSchedule(
                                ScheduleEntity(
                                    title = row.title.trim(),
                                    date = date,
                                    startTime = LocalTime.of(9, 0),
                                    endTime = LocalTime.of(10, 0),
                                    milestoneId = parentId,
                                    tagIds = tags,
                                    location = row.location.trim()
                                ),
                                emptySet()
                            )
                            created++
                            setRows(_rows.value.filterNot { it.id == row.id })
                        }
                    }
                }
                pruneFinished()
                onDone(created, goalIds.distinct())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(created)
            }
        }
    }

    private fun markExisting(rowId: String, id: String) {
        setRows(_rows.value.map { if (it.id == rowId) it.copy(existingId = id) else it })
    }

    /** 登録済みになった大目的・中日程のうち、下に未登録の行が残っていないものを下書きから外す。 */
    private fun pruneFinished() {
        val list = _rows.value
        val parents = parentsOf(list)
        fun hasPendingDescendant(row: PlanRow): Boolean = list.any { child ->
            parents[child.id]?.id == row.id && (child.existingId == null || hasPendingDescendant(child))
        }
        setRows(list.filter { it.existingId == null || hasPendingDescendant(it) })
    }
}
