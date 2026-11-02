package com.kingshot.camera.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kingshot.camera.R
import com.kingshot.camera.update.UpdateUi

/** Full-screen settings. Everything beyond taking a photo lives here: processing requests, RAW, recent photos, diagnostics, about. */
@Composable
fun SettingsSheet(state: CameraUiState, vm: CameraViewModel, onAllowInstalls: () -> Unit) {
    val closeDescription = stringResource(R.string.close)
    Column(Modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.settings), Modifier.weight(1f), style = Type.Title)
            Box(
                Modifier
                    .size(48.dp)
                    .background(Palette.SurfaceHigh, CircleShape)
                    .clickable(role = Role.Button) { vm.closeSettings() }
                    .semantics { contentDescription = closeDescription },
                contentAlignment = Alignment.Center
            ) {
                KsIconView(KsIcon.CLOSE, Palette.Text)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            ProcessingSection(state, vm)
            FormatSection(state, vm)
            PhotosSection(state, vm)
            UpdatesSection(state, vm, onAllowInstalls)
            DiagnosticsSection(state, vm)
            AboutSection(state)
        }
    }
}

@Composable
private fun ProcessingSection(state: CameraUiState, vm: CameraViewModel) {
    SectionCard(stringResource(R.string.section_processing)) {
        BasicText(stringResource(R.string.profile_line, state.profileLabel), Modifier.padding(top = 8.dp), style = Type.Body)
        Hint(stringResource(R.string.processing_disclosure))
        Divider()
        SwitchRow(
            stringResource(R.string.request_nr_off), state.requestNr && state.nrAvailable, state.nrAvailable, vm::setRequestNr,
            hint = if (state.nrAvailable) null else stringResource(R.string.not_listed)
        )
        Divider()
        SwitchRow(
            stringResource(R.string.request_edge_off), state.requestEdge && state.edgeAvailable, state.edgeAvailable, vm::setRequestEdge,
            hint = if (state.edgeAvailable) null else stringResource(R.string.not_listed)
        )
    }
}

@Composable
private fun FormatSection(state: CameraUiState, vm: CameraViewModel) {
    val status = when {
        !state.rawAvailable -> R.string.raw_status_unavailable
        state.wantRaw && state.rawActive -> R.string.raw_status_active
        state.wantRaw -> R.string.raw_status_pending
        else -> R.string.raw_status_off
    }
    SectionCard(stringResource(R.string.section_format)) {
        SwitchRow(stringResource(R.string.raw_dng), state.wantRaw && state.rawAvailable, state.rawAvailable, vm::setWantRaw)
        Hint(stringResource(status))
    }
}

@Composable
private fun PhotosSection(state: CameraUiState, vm: CameraViewModel) {
    SectionCard(stringResource(R.string.section_photos)) {
        if (state.photos.isEmpty()) {
            Hint(stringResource(R.string.no_photos))
        }
        state.photos.forEach { ph ->
            Column(Modifier.padding(vertical = 6.dp)) {
                BasicText(ph.name, style = Type.Body)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(R.string.open), { vm.openPhoto(ph.uri) })
                    ActionButton(stringResource(R.string.details), { vm.showDetails(ph.name) })
                }
            }
            Divider()
        }
        state.details?.let {
            BasicText(it, Modifier.padding(vertical = 8.dp), style = Type.Caption.copy(color = Palette.Text))
            ActionButton(stringResource(R.string.close_details), vm::closeDetails, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun DiagnosticsSection(state: CameraUiState, vm: CameraViewModel) {
    var showLog by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.section_diagnostics)) {
        BasicText(stringResource(R.string.selfcheck_title), Modifier.padding(top = 8.dp), style = Type.Body)
        Hint(stringResource(R.string.selfcheck_body))
        ActionButton(
            stringResource(if (state.selfCheckRunning) R.string.selfcheck_running else R.string.selfcheck_run),
            vm::runSelfCheck,
            Modifier.padding(bottom = 8.dp),
            enabled = state.cameraReady && !state.selfCheckRunning,
            primary = true
        )
        state.selfCheckText?.let { BasicText(it, Modifier.padding(bottom = 8.dp), style = Type.Caption.copy(color = Palette.Text)) }
        Divider()
        SwitchRow(stringResource(R.string.include_samples), state.includeSamples, true, vm::setIncludeSamples)
        ActionButton(stringResource(R.string.export_bundle), { vm.exportBundle() }, Modifier.padding(bottom = 8.dp))
        Divider()
        ActionButton(
            stringResource(if (showLog) R.string.hide_log else R.string.show_log),
            { showLog = !showLog },
            Modifier.padding(vertical = 8.dp)
        )
        if (showLog) {
            BasicText(state.logLines.joinToString("\n"), Modifier.padding(bottom = 8.dp), style = Type.Mono)
        }
    }
}

@Composable
private fun UpdatesSection(state: CameraUiState, vm: CameraViewModel, onAllowInstalls: () -> Unit) {
    val context = LocalContext.current
    val u = state.update
    SectionCard(stringResource(R.string.section_updates)) {
        BasicText(stringResource(R.string.about_version, state.appVersion), Modifier.padding(top = 8.dp), style = Type.Body)
        Hint(stringResource(R.string.updates_body))
        val busy = u is UpdateUi.Checking || u is UpdateUi.Downloading || u is UpdateUi.Installing
        val status: String? = when (u) {
            UpdateUi.Idle -> null
            UpdateUi.Checking -> stringResource(R.string.updates_checking)
            is UpdateUi.UpToDate -> stringResource(R.string.updates_up_to_date, u.installed)
            is UpdateUi.Available -> stringResource(R.string.updates_available, u.version, Formatter.formatShortFileSize(context, u.sizeBytes))
            is UpdateUi.NeedsInstallPermission -> stringResource(R.string.updates_needs_permission)
            is UpdateUi.Downloading -> stringResource(R.string.updates_downloading, u.percent)
            UpdateUi.Installing -> stringResource(R.string.updates_installing)
            is UpdateUi.Failed -> stringResource(R.string.updates_failed, u.message)
        }
        val statusColor = if (u is UpdateUi.Failed) Palette.Danger else Palette.Text
        status?.let { BasicText(it, Modifier.padding(bottom = 8.dp), style = Type.Body.copy(color = statusColor)) }
        if (u is UpdateUi.Available && u.notes.isNotBlank()) Hint(u.notes)
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(R.string.updates_check), vm::checkForUpdates, enabled = !busy)
            when (u) {
                is UpdateUi.Available -> ActionButton(stringResource(R.string.updates_install), vm::installUpdate, primary = true)
                is UpdateUi.NeedsInstallPermission ->
                    ActionButton(stringResource(R.string.updates_open_install_settings), onAllowInstalls, primary = true)
                else -> Unit
            }
        }
        if (u is UpdateUi.NeedsInstallPermission) {
            ActionButton(stringResource(R.string.updates_install), vm::installUpdate, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun AboutSection(state: CameraUiState) {
    SectionCard(stringResource(R.string.section_about)) {
        BasicText(stringResource(R.string.app_name), Modifier.padding(top = 8.dp), style = Type.Body)
        Hint(stringResource(R.string.about_privacy))
    }
}
