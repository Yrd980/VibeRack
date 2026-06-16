package com.viberack.app.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun QuantityOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    showUndo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    undoContentDescription: String? = null,
    onDecrease: (() -> Unit)? = null,
    decreaseContentDescription: String? = null,
    onIncrease: (() -> Unit)? = null,
    increaseContentDescription: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { labelText -> { Text(text = labelText) } },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        trailingIcon = {
            Row {
                UndoTrailingIconButton(
                    enabled = enabled,
                    showUndo = showUndo,
                    onUndo = onUndo,
                    undoContentDescription = undoContentDescription
                )
                if (onDecrease != null) {
                    IconButton(
                        onClick = onDecrease,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = decreaseContentDescription ?: "Decrease"
                        )
                    }
                }
                if (onIncrease != null) {
                    IconButton(
                        onClick = onIncrease,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = increaseContentDescription ?: "Increase"
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SourceOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    showUndo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    undoContentDescription: String? = null,
    onValueBlurTransform: ((String) -> String)? = null,
    showOpen: Boolean = false,
    onOpen: (() -> Unit)? = null,
    openContentDescription: String? = null
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            if (hadFocus && !focusState.isFocused) {
                val normalizedValue = onValueBlurTransform?.invoke(value) ?: value
                if (normalizedValue != value) {
                    onValueChange(normalizedValue)
                }
            }
            hadFocus = focusState.isFocused
        },
        label = label?.let { labelText -> { Text(text = labelText) } },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        trailingIcon = if (showUndo || (showOpen && onOpen != null)) {
            {
                Row {
                    UndoTrailingIconButton(
                        enabled = enabled,
                        showUndo = showUndo,
                        onUndo = onUndo,
                        undoContentDescription = undoContentDescription
                    )
                    if (showOpen && onOpen != null) {
                        IconButton(
                            onClick = onOpen,
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = openContentDescription ?: "Open"
                            )
                        }
                    }
                }
            }
        } else {
            null
        }
    )
}

@Composable
fun Modifier.clearFocusOnTapOutside(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        detectTapGestures {
            focusManager.clearFocus(force = true)
        }
    }
}

@Composable
private fun UndoTrailingIconButton(
    enabled: Boolean,
    showUndo: Boolean,
    onUndo: (() -> Unit)?,
    undoContentDescription: String?
) {
    if (!showUndo || onUndo == null) {
        return
    }
    IconButton(
        onClick = onUndo,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Undo,
            contentDescription = undoContentDescription ?: "Undo"
        )
    }
}
