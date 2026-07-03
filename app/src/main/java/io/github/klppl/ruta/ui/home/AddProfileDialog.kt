package io.github.klppl.ruta.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Names a new account profile before it exists. The suggested name arrives pre-selected so a
 * single keystroke replaces it, and the explainer is the one place the profile concept is
 * introduced — keep it to a sentence.
 */
@Composable
fun AddProfileDialog(
    suggestedName: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember {
        mutableStateOf(TextFieldValue(suggestedName, selection = TextRange(0, suggestedName.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column {
                Text(
                    text = "An account keeps its own sign-ins and cookies, separate from the others — handy for a second Reddit or Mastodon login.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Work, Alt, …") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.text.trim()) },
                enabled = name.text.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
