package com.kongjjj.obscontroller.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp

data class FilterTypeEntry(val label: String, val kind: String)

val AUDIO_FILTER_TYPES = listOf(
    FilterTypeEntry("Gain", "gain_filter"),
    FilterTypeEntry("Noise Gate", "noise_gate_filter"),
    FilterTypeEntry("Compressor", "compressor_filter"),
    FilterTypeEntry("Noise Suppression", "noise_suppress_filter"),
    FilterTypeEntry("Limiter", "limiter_filter"),
    FilterTypeEntry("Expander / Gate", "expander_filter"),
)

val VIDEO_FILTER_TYPES = listOf(
    FilterTypeEntry("Color Correction", "color_correction_filter"),
    FilterTypeEntry("Sharpen", "sharpness_filter"),
    FilterTypeEntry("Crop / Pad", "crop_filter"),
    FilterTypeEntry("Render Delay", "gpu_delay"),
    FilterTypeEntry("Chroma Key", "chroma_key_filter"),
    FilterTypeEntry("Color Key", "color_key_filter"),
)

@Composable
fun AddFilterDialog(
    title: String = "Add Filter",
    filterTypes: List<FilterTypeEntry> = AUDIO_FILTER_TYPES,
    onAdd: (filterName: String, filterKind: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        }
    }

    var filterName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(filterTypes.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { hideKeyboard() },
        title = {
            Text(
                title,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { hideKeyboard() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { hideKeyboard() },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it },
                    label = { Text("Filter Name") },
                    placeholder = { Text(selectedType.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Filter Type", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                filterTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(type.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val name = filterName.ifBlank { selectedType.label }
                onAdd(name, selectedType.kind)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
