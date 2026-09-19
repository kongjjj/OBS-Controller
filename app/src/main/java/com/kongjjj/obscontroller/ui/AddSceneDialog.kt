package com.kongjjj.obscontroller.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun AddSceneDialog(
    onAdd: (sceneName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sceneName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新場景") },
        text = {
            OutlinedTextField(
                value = sceneName,
                onValueChange = { sceneName = it },
                label = { Text("場景名稱") },
                placeholder = { Text("My Scene") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onAdd(sceneName.trim()) },
                enabled = sceneName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
