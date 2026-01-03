/*
 * Copyright (c) 2025 Nishant Mishra
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.nsh07.pomodoro.ui.statsScreen

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill

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

    val radius = with(LocalDensity.current) {
        (thickness / 2).toPx()
    }

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val primaryColor = MaterialTheme.colorScheme.primary

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        ColumnCartesianLayer.ColumnProvider.series(
                            dataValues.indices.map { _ ->
                                rememberLineComponent(
                                    fill = fill(primaryColor),
                                    thickness = thickness,
                                    shape = { _, path, left, top, right, bottom ->
                                        if (top + radius <= bottom - radius) {
                                            path.arcTo(
                                                RectF(left, top, right, top + 2 * radius),
                                                180f,
                                                180f
                                            )
                                            path.lineTo(right, bottom - radius)
                                            path.arcTo(
                                                RectF(left, bottom - 2 * radius, right, bottom),
                                                0f,
                                                180f
                                            )
                                            path.close()
                                        } else {
                                            path.addCircle(
                                                left + radius,
                                                bottom - radius,
                                                radius,
                                                Path.Direction.CW
                                            )
                                        }
                                    }
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
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
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
                                val availableWidth = chartWidth - startAxisWidth - endPadding
                                val availableHeight = chartHeight - bottomAxisHeight - topPadding

                                val columnWidth = with(density) { thickness.toPx() }
                                val spacing = with(density) { columnCollectionSpacing.toPx() }
                                val totalColumnWidth = columnWidth + spacing

                                val clickX = offset.x - startAxisWidth
                                val clickY = offset.y - topPadding

                                if (clickX in 0.0f..availableWidth && clickY >= 0 && clickY <= availableHeight) {
                                    val columnIndex = (clickX / totalColumnWidth).toInt()
                                    if (columnIndex >= 0 && columnIndex < dataValues.size) {
                                        val maxValue = dataValues.maxOrNull() ?: 1f
                                        val barHeightRatio =
                                            if (maxValue > 0) dataValues[columnIndex] / maxValue else 0f
                                        val barHeight = availableHeight * barHeightRatio
                                        val barTop = availableHeight - barHeight

                                        if (clickY in barTop..availableHeight) {
                                            onColumnClick(columnIndex)
                                        }
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
