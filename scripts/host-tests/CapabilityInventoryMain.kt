package com.jumlo.app.inventory

import com.jumlo.app.data.jobs.CapabilityRegistry

fun main() {
    CapabilityRegistry.tools.forEach { tool ->
        val state = CapabilityRegistry.availability(tool.id)
        println(listOf(tool.id, tool.titleEn, tool.pillar.name, state.visible, state.enabled,
            state.kind, state.evidence, state.reason ?: "").joinToString("\t"))
    }
}
