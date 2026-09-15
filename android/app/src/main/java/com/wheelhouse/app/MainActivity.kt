package com.wheelhouse.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wheelhouse.app.data.WheelHouseApiClient
import com.wheelhouse.app.ui.components.WheelHouseSection
import com.wheelhouse.app.ui.home.HomeDashboardScreen
import com.wheelhouse.app.ui.home.sampleHomeDashboardState
import com.wheelhouse.app.ui.positions.PositionDetailScreen
import com.wheelhouse.app.ui.positions.PositionDetailState
import com.wheelhouse.app.ui.positions.PositionsScreen
import com.wheelhouse.app.ui.positions.samplePositionDetailState
import com.wheelhouse.app.ui.positions.samplePositionsBoardState
import com.wheelhouse.app.ui.risk.ConcentrationSlice
import com.wheelhouse.app.ui.risk.RiskScreen
import com.wheelhouse.app.ui.risk.sampleRiskState
import com.wheelhouse.app.ui.theme.WheelHouseTheme
import kotlinx.coroutines.launch
import java.io.IOException

private const val LOG_TAG = "WheelHouseApi"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WheelHouseTheme {
                val scope = rememberCoroutineScope()

                // Three screens don't justify a nav graph yet; this switch is the
                // placeholder that becomes one when a fourth surface lands.
                var section by rememberSaveable { mutableStateOf(WheelHouseSection.HOME) }
                var selectedPositionTicker by rememberSaveable { mutableStateOf<String?>(null) }

                // Server-backed state, each starting on the same sample*State() the
                // screens have always defaulted to — the mock API (api/mock-server/)
                // can only improve on this, never gate the app on being reachable.
                var homeState by remember { mutableStateOf(sampleHomeDashboardState()) }
                var positionsState by remember { mutableStateOf(samplePositionsBoardState()) }
                var positionDetails by remember {
                    mutableStateOf<Map<String, PositionDetailState>>(mapOf("MSFT" to samplePositionDetailState()))
                }
                var riskState by remember { mutableStateOf(sampleRiskState()) }
                var paused by rememberSaveable { mutableStateOf(false) }
                var syncedAgo by remember { mutableStateOf("42s") }
                var book by remember { mutableStateOf("BASE") }

                suspend fun refresh() {
                    try {
                        val status = WheelHouseApiClient.fetchStatus()
                        val pnl = WheelHouseApiClient.fetchPnl()
                        val positions = WheelHouseApiClient.fetchPositions()
                        val decisions = WheelHouseApiClient.fetchDecisions()
                        val risk = WheelHouseApiClient.fetchRisk()

                        syncedAgo = "${status.dataAgeS}s"
                        book = status.book
                        paused = status.paused

                        homeState = homeState.copy(
                            syncedAgo = syncedAgo,
                            book = book,
                            windows = pnl.windows,
                            capital = pnl.capital,
                            positions = positions.dashboardSummaries,
                            decisions = decisions,
                        )
                        positionsState = positionsState.copy(
                            syncedAgo = syncedAgo,
                            book = book,
                            aggregates = positionsState.aggregates.copy(
                                marginUtilizationPct = risk.marginUtilizationPct,
                                netExposure = risk.netExposure,
                                netExposureMeterPct = risk.netExposureMeterPct,
                                syntheticCash = risk.syntheticCash,
                                syntheticCashMeterPct = risk.syntheticCashMeterPct,
                            ),
                            rows = positions.boardRows,
                        )
                        positionDetails = positions.detailByTicker
                        riskState = riskState.copy(
                            syncedAgo = syncedAgo,
                            book = book,
                            assignAllCost = risk.assignAllCost,
                            cashMarginAvailable = risk.cashMarginAvailable,
                            concentration = risk.concentration.map { (underlying, pct) -> ConcentrationSlice(underlying, pct) },
                        )
                    } catch (e: IOException) {
                        Log.w(LOG_TAG, "Couldn't reach the mock server (api/mock-server/) — showing local fixtures", e)
                    }
                }

                LaunchedEffect(Unit) { refresh() }

                // WheelHouseSection only lists built sections now (Journal/Backtests
                // stay off the nav until each has a screen), so every tap here is
                // already a built screen — no unbuilt-tab gating needed.
                val onNavSelect: (WheelHouseSection) -> Unit = {
                    section = it
                    selectedPositionTicker = null
                }
                // The kill switch round-trips through POST /v1/pause | /v1/resume — the
                // local flag only updates once the server confirms, same discipline
                // approvals already follow (no optimistic UI for a real state change).
                val onToggleEntries: () -> Unit = {
                    scope.launch {
                        try {
                            paused = WheelHouseApiClient.setPaused(!paused)
                        } catch (e: IOException) {
                            Log.w(LOG_TAG, "Couldn't reach the mock server to toggle pause", e)
                        }
                    }
                }
                // Approve/veto/snooze/branch-confirm all funnel here — POST
                // /v1/decisions/{id}, then a full refresh so the resolved card's
                // disappearance (and anything else that changed) comes from the server,
                // not a local guess about what resolving it should look like.
                val onOptionSelected: (String, String, String?) -> Unit = { decisionId, optionId, reason ->
                    scope.launch {
                        try {
                            WheelHouseApiClient.resolveDecision(decisionId, optionId, reason)
                            refresh()
                        } catch (e: IOException) {
                            Log.w(LOG_TAG, "Couldn't resolve decision $decisionId", e)
                        }
                    }
                }
                // The board's drill-down only has fixtures for tickers the server sent a
                // `detail` object for (MSFT today) — other rows don't navigate yet.
                val onRowClick: (String) -> Unit = { ticker ->
                    if (positionDetails.containsKey(ticker)) selectedPositionTicker = ticker
                }

                when {
                    section == WheelHouseSection.POSITIONS && selectedPositionTicker != null ->
                        positionDetails[selectedPositionTicker]?.let { detail ->
                            PositionDetailScreen(
                                state = detail.copy(syncedAgo = syncedAgo, book = book),
                                paused = paused,
                                onBack = { selectedPositionTicker = null },
                                onNavSelect = onNavSelect,
                            )
                        }
                    section == WheelHouseSection.RISK -> RiskScreen(
                        state = riskState,
                        paused = paused,
                        onToggleEntries = onToggleEntries,
                        onNavSelect = onNavSelect,
                    )
                    section == WheelHouseSection.POSITIONS -> PositionsScreen(
                        state = positionsState,
                        paused = paused,
                        onNavSelect = onNavSelect,
                        onRowClick = onRowClick,
                    )
                    else -> HomeDashboardScreen(
                        state = homeState,
                        paused = paused,
                        onNavSelect = onNavSelect,
                        onOptionSelected = onOptionSelected,
                    )
                }
            }
        }
    }
}
