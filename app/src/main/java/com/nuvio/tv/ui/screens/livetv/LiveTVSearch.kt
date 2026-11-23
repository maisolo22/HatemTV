package com.nuvio.tv.ui.screens.livetv

import java.text.Normalizer
import java.util.Locale

private fun channelSearchKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)

internal fun channelStartsWith(name: String, query: String): Boolean =
    channelSearchKey(name).startsWith(channelSearchKey(query))
