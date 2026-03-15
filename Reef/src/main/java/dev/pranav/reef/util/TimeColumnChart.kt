/*
 * Copyright (c) 2025 Nishant Mishra
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.nsh07.pomodoro.ui.statsScreen

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimeColumnChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    thickness: Dp = 20.dp,
    columnCollectionSpacing: Dp = 28.dp,
    xValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    yValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    animationSpec: AnimationSpec<Float>? = motionScheme.slowEffectsSpec(),
    onColumnClick: ((Int) -> Unit)? = null,
    dataValues: List<Float> = emptyList(),
    selectedColumnIndex: Int? = null
) {
    if (dataValues.isEmpty()) return

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary

    // Use a dedicated scrollState variable to track horizontal movement
    val vicoScrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        dataValues.indices.map { _ ->
                            rememberLineComponent(
                                fill = Fill(primaryColor),
                                thickness = thickness,
                                shape = MarkerCornerBasedShape(RoundedCornerShape(16.dp))
                            )
                        }
                    ),
                    columnCollectionSpacing = columnCollectionSpacing
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = yValueFormatter
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    guideline = rememberLineComponent(Fill.Transparent),
                    valueFormatter = xValueFormatter
                )
            ),
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = Zoom.fixed(),
                minZoom = Zoom.min(Zoom.Content, Zoom.fixed())
            ),
            scrollState = vicoScrollState,
            animationSpec = animationSpec,
            modifier = modifier
                .onSizeChanged { chartSize = it }
                .then(
                    if (onColumnClick != null && dataValues.isNotEmpty()) {
                        Modifier.pointerInput(dataValues, selectedColumnIndex) {
                            detectTapGestures { offset ->
                                val chartWidth = chartSize.width.toFloat()
                                val chartHeight = chartSize.height.toFloat()
                                val startAxisWidth = with(density) { 48.dp.toPx() }
                                val endPadding = with(density) { 16.dp.toPx() }
                                val bottomAxisHeight = with(density) { 32.dp.toPx() }
                                val topPadding = with(density) { 8.dp.toPx() }
                                
                                val availableHeight = chartHeight - bottomAxisHeight - topPadding

                                val columnWidth = with(density) { thickness.toPx() }
                                val spacing = with(density) { columnCollectionSpacing.toPx() }
                                val totalColumnWidth = columnWidth + spacing

                                // Correcting coordinate: Add current scroll offset to the tap position relative to the chart start
                                val scrollOffset = vicoScrollState.value
                                val relativeTapX = offset.x - startAxisWidth
                                val absoluteX = relativeTapX + scrollOffset
                                val clickY = offset.y - topPadding

                                // Ensure tap is within the horizontal bounds of the chart content
                                if (offset.x in startAxisWidth..(chartWidth - endPadding) && 
                                    clickY in 0f..availableHeight) {
                                    
                                    // Using integer division for more predictable column mapping
                                    val columnIndex = (absoluteX / totalColumnWidth).toInt()
                                        .coerceIn(0, dataValues.size - 1)

                                    val maxValue = dataValues.maxOrNull() ?: 1f
                                    val barHeightRatio = if (maxValue > 0) dataValues[columnIndex] / maxValue else 0f
                                    val barHeight = availableHeight * barHeightRatio
                                    val barTop = availableHeight - barHeight

                                    // Trigger callback if the tap falls within the column's vertical area
                                    if (clickY in barTop..availableHeight) {
                                        onColumnClick(columnIndex)
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimeLineChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    xValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    yValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    animationSpec: AnimationSpec<Float>? = motionScheme.slowEffectsSpec(),
    dataValues: List<Float> = emptyList(),
) {
    if (dataValues.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary

    val areaBrush = remember(primaryColor) {
        Brush.verticalGradient(
            listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)
        )
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(primaryColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(Fill(areaBrush)),
                            pointConnector = LineCartesianLayer.PointConnector.cubic(),
                        )
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = yValueFormatter
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    guideline = null,
                    valueFormatter = xValueFormatter,
                    itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 1 }) }
                ),
            ),
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(zoomEnabled = false),
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            animationSpec = animationSpec,
            modifier = modifier
        )
    }
}