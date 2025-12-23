package com.example.aiagentchat.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.aiagentchat.domain.model.AiModel

@Composable
fun ModelSwitcher(
    currentModel: AiModel,
    availableModels: List<AiModel>,
    configuredModels: Set<AiModel>,
    onModelChange: (AiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentModel in configuredModels) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentModel.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select model",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                val isConfigured = model in configuredModels
                val isSelected = model == currentModel
                
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConfigured) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = model.displayName,
                                color = if (isConfigured) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                            if (!isConfigured) {
                                Text(
                                    text = " (no key)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    onClick = {
                        onModelChange(model)
                        expanded = false
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}






