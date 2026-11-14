package app.itivio.ui.itinerary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.BoardingHint
import app.itivio.domain.StationExit
import app.itivio.domain.TrainPart
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.label

/**
 * Where to board the train: the part of the train, the reason (a transfer or the exit), and a
 * drawing of a train with that part filled. The text names the part, so color is not the only cue.
 */
@Composable
internal fun BoardingRow(hint: BoardingHint, trainPart: TrainPart) {
    val scheme = MaterialTheme.colorScheme
    val part = stringResource(trainPart.label())
    val transfer = hint.transfer
    val exitNumber = hint.exit?.number?.takeIf { it.isNotBlank() }
    val exitText = if (exitNumber != null) stringResource(R.string.boarding_exit_number, exitNumber) else stringResource(R.string.boarding_exit)
    val reason = if (transfer != null) {
        stringResource(R.string.boarding_transfer_description, stringResource(transfer.mode.label()), transfer.line.name)
    } else {
        exitText
    }
    val description = stringResource(R.string.boarding_description, part, reason)
    Surface(
        color = scheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.boarding_title), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(part, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (transfer != null) stringResource(R.string.boarding_transfer) else exitText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (transfer != null) LineBadge(transfer.line, transfer.mode)
                }
            }
            TrainDrawing(trainPart, Modifier.size(width = 104.dp, height = 24.dp))
        }
    }
}

/**
 * The best exit of the station, below the alighting stop: its number and its name. The data
 * gives the exit nearest to the destination; the walk can use another exit.
 */
@Composable
internal fun ExitRow(exit: StationExit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val number = exit.number?.takeIf { it.isNotBlank() }
    val name = if (number != null) stringResource(R.string.boarding_exit_name, number, exit.name) else exit.name
    val description = stringResource(R.string.boarding_best_exit_description, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.boarding_best_exit), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A train of three parts: the rear, the middle, and the front, with the nose on the right in
 * the direction of travel. [part] is filled with the primary color.
 */
@Composable
private fun TrainDrawing(part: TrainPart, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val on = scheme.primary
    val off = scheme.outline
    val windowOn = scheme.onPrimary
    val windowOff = scheme.surfaceContainerHighest
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val car = (size.width - gap * 2) / 3
        // Left to right: the rear, the middle, then the front with its nose.
        listOf(TrainPart.REAR, TrainPart.MIDDLE, TrainPart.FRONT).forEachIndexed { index, each ->
            val left = index * (car + gap)
            val filled = each == part
            drawCar(each, left, car, if (filled) on else off, if (filled) windowOn else windowOff)
        }
    }
}

private fun DrawScope.drawCar(part: TrainPart, left: Float, width: Float, body: Color, window: Color) {
    val height = size.height
    val small = 3.dp.toPx()
    val round = height / 2.5f
    val shape = when (part) {
        TrainPart.REAR -> RoundRect(Rect(left, 0f, left + width, height),
            topLeft = CornerRadius(round), topRight = CornerRadius(small), bottomRight = CornerRadius(small), bottomLeft = CornerRadius(round))
        TrainPart.MIDDLE -> RoundRect(left, 0f, left + width, height, CornerRadius(small))
        // The nose slopes down to the front.
        TrainPart.FRONT -> RoundRect(Rect(left, 0f, left + width, height),
            topLeft = CornerRadius(small), topRight = CornerRadius(width * 0.55f, height * 0.75f),
            bottomRight = CornerRadius(small), bottomLeft = CornerRadius(small))
    }
    drawPath(Path().apply { addRoundRect(shape) }, body)
    // Three windows; the front car has two and a windscreen.
    val top = height * 0.22f
    val windowHeight = height * 0.28f
    val windowWidth = width * 0.16f
    val count = if (part == TrainPart.FRONT) 2 else 3
    val first = left + width * (if (part == TrainPart.FRONT) 0.12f else 0.17f)
    repeat(count) { index ->
        drawRoundRect(window, Offset(first + index * width * 0.25f, top), Size(windowWidth, windowHeight), CornerRadius(small / 2))
    }
}

private fun TrainPart.label(): Int = when (this) {
    TrainPart.FRONT -> R.string.boarding_front
    TrainPart.MIDDLE -> R.string.boarding_middle
    TrainPart.REAR -> R.string.boarding_rear
}
