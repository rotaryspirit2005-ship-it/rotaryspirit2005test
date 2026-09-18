package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ROW_HEIGHT = 72.dp
private val ROW_GAP = 28.dp
private val ROW_STEP = ROW_HEIGHT + ROW_GAP
private val CHIP_WIDTH = 70.dp
private val CHIP_HEIGHT = 56.dp
private val TRUNK_WIDTH = 190.dp
private val MARGIN = 20.dp
private val GAP = 10.dp
private val TRUNK_CONNECTOR_COLOR = Color(0xFFC9C4B6)

/**
 * 予定を時刻順のトランクとして縦に並べ、リンクのある予定だけ
 * 左右どちらかに短い枝チップを伸ばして表示する。ズーム・パン操作は
 * [ZoomPanBox] が担当する。
 */
@Composable
fun FlowScreen(
    rows: List<FlowRow>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val trunkX = MARGIN + CHIP_WIDTH + GAP
    val leftChipX = MARGIN
    val rightChipX = trunkX + TRUNK_WIDTH + GAP
    val trunkCenterX = trunkX + TRUNK_WIDTH / 2
    val contentWidth = rightChipX + CHIP_WIDTH + MARGIN
    val contentHeight = if (rows.isEmpty()) 0.dp else ROW_STEP * rows.size - ROW_GAP

    ZoomPanBox(modifier = modifier) {
        Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
            FlowConnectors(
                rows = rows,
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                trunkX = trunkX,
                trunkCenterX = trunkCenterX,
                leftChipX = leftChipX,
                rightChipX = rightChipX
            )

            rows.forEachIndexed { index, row ->
                val rowTop = ROW_STEP * index
                TrunkCard(
                    row = row,
                    modifier = Modifier
                        .offset(x = trunkX, y = rowTop)
                        .width(TRUNK_WIDTH)
                        .height(ROW_HEIGHT),
                    onClick = { onItemClick(row.schedule.id) }
                )

                val branch = row.branches.firstOrNull()
                if (branch != null) {
                    val chipX = if (row.side == BranchSide.RIGHT) rightChipX else leftChipX
                    val chipTop = rowTop + (ROW_HEIGHT - CHIP_HEIGHT) / 2
                    BranchChip(
                        branch = branch,
                        overflowCount = row.overflowCount,
                        modifier = Modifier
                            .offset(x = chipX, y = chipTop)
                            .width(CHIP_WIDTH)
                            .height(CHIP_HEIGHT),
                        onClick = { onItemClick(branch.target.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowConnectors(
    rows: List<FlowRow>,
    contentWidth: Dp,
    contentHeight: Dp,
    trunkX: Dp,
    trunkCenterX: Dp,
    leftChipX: Dp,
    rightChipX: Dp
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.width(contentWidth).height(contentHeight)) {
        val rowStepPx = ROW_STEP.toPx()
        val rowHeightPx = ROW_HEIGHT.toPx()
        val trunkCenterXPx = trunkCenterX.toPx()

        for (i in 0 until rows.size - 1) {
            val bottomY = i * rowStepPx + rowHeightPx
            val topY = (i + 1) * rowStepPx
            drawLine(
                color = TRUNK_CONNECTOR_COLOR,
                start = Offset(trunkCenterXPx, bottomY + 3.dp.toPx()),
                end = Offset(trunkCenterXPx, topY - 6.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        rows.forEachIndexed { index, row ->
            if (row.branches.isNotEmpty()) {
                val centerY = index * rowStepPx + rowHeightPx / 2f
                val fromX = if (row.side == BranchSide.RIGHT) (trunkX + TRUNK_WIDTH).toPx() else trunkX.toPx()
                val toX = if (row.side == BranchSide.RIGHT) rightChipX.toPx() else (leftChipX + CHIP_WIDTH).toPx()
                drawLine(
                    color = accent,
                    start = Offset(fromX, centerY),
                    end = Offset(toX, centerY),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(fromX, centerY))
            }
        }
    }
}

@Composable
private fun TrunkCard(row: FlowRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val schedule = row.schedule
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = "${schedule.startTime}〜${schedule.endTime}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = schedule.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (row.linkedCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = row.linkedCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun BranchChip(
    branch: FlowBranch,
    overflowCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = branch.target.startTime.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = branch.target.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflowCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
