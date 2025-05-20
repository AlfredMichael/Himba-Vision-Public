package ie.tus.himbavision.utility.Other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

@Composable
fun rememberSaveableDirections(): SnapshotStateList<String> {
    val directionsSaver: Saver<SnapshotStateList<String>, Any> = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )
    return rememberSaveable(saver = directionsSaver) { mutableStateListOf() }
}
