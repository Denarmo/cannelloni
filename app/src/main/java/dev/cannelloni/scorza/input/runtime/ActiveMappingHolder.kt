package dev.cannelloni.scorza.input.runtime

import dev.cannelloni.scorza.input.DeviceMapping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveMappingHolder {

    private val _active = MutableStateFlow<DeviceMapping?>(null)
    val active: StateFlow<DeviceMapping?> = _active.asStateFlow()

    fun set(mapping: DeviceMapping) {
        _active.value = mapping
    }

    fun clear() {
        _active.value = null
    }
}

fun DeviceMapping?.confirmButton(): dev.cannelloni.ui.ConfirmButton = when {
    this == null -> dev.cannelloni.ui.ConfirmButton.EAST
    this.menuConfirm == dev.cannelloni.scorza.input.CanonicalButton.BTN_EAST -> dev.cannelloni.ui.ConfirmButton.EAST
    else -> dev.cannelloni.ui.ConfirmButton.SOUTH
}

fun DeviceMapping?.labelSet(fallback: dev.cannelloni.ui.ButtonLabelSet): dev.cannelloni.ui.ButtonLabelSet =
    when (this?.glyphStyle) {
        dev.cannelloni.scorza.input.GlyphStyle.PLUMBER -> dev.cannelloni.ui.ButtonLabelSet.PLUMBER
        dev.cannelloni.scorza.input.GlyphStyle.REDMOND -> dev.cannelloni.ui.ButtonLabelSet.REDMOND
        dev.cannelloni.scorza.input.GlyphStyle.SHAPES -> dev.cannelloni.ui.ButtonLabelSet.SHAPES
        null -> fallback
    }
