package dev.cannelloni.scorza.input

data class ResolvedPortConfig(
    val port: Int,
    val mappingId: String?,
    val resolvedBindings: Map<CanonicalButton, List<InputBinding>>,
    val controllerTypeId: Int,
)
