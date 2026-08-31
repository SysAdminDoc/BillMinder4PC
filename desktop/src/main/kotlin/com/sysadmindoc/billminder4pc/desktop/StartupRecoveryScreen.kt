package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.theme.CatRed

@Composable
internal fun StartupRecoveryScreen(
    failureMessage: String,
    dataDirectory: String,
    logFile: String,
    actionError: String? = null,
    onOpenDataFolder: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.width(620.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(28.dp)) {
                Text(
                    "BillMinder couldn't open its data",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CatRed
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your database wasn't changed. Open the data folder to back it up or replace " +
                        "the damaged file, then restart BillMinder.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(18.dp))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(failureMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Data: $dataDirectory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Log: $logFile", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                actionError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = CatRed)
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onOpenDataFolder,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open data folder")
                    }
                }
            }
        }
    }
}
