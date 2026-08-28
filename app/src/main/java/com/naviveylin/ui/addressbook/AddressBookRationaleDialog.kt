package com.naviveylin.ui.addressbook

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naviveylin.R

/**
 * One-time rationale dialog shown before the first READ_CONTACTS request
 * (spec: address-book-permission — first-use rationale dialog). Explains why
 * access is requested, states that it is optional and can be denied, and only
 * offers the continue action that triggers the actual permission request.
 *
 * [onContinue] is the only path that leads to the system permission request;
 * [onNotNow] and [onDismiss] decline without requesting. The caller records
 * the decision in every path (see [AddressBookRationaleStore]).
 */
@Composable
fun AddressBookRationaleDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.address_book_rationale_title)) },
        text = { Text(stringResource(R.string.address_book_rationale_text)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.address_book_rationale_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.address_book_rationale_not_now))
            }
        }
    )
}
