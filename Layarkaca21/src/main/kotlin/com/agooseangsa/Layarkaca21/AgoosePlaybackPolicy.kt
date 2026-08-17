package com.agooseangsa.Layarkaca21

internal data class _l0(
    val eligibleCandidateCount: Int,
    val distinctHostCount: Int = 0,
    val distinctExtractorFamilyCount: Int = 0,
    val hasUserFacingServerChoices: Boolean = false,
    val hasResolutionDiversity: Boolean = false,
    val mirrorOnlyProven: Boolean = false,
)

internal fun _k0._l1(evidence: _l0): _k0 {
    if (this != _k0.AUTO) return this
    if (evidence.eligibleCandidateCount <= 1 || evidence.mirrorOnlyProven) return _k0.FIRST_SUCCESS
    if (
        evidence.hasUserFacingServerChoices ||
        evidence.hasResolutionDiversity ||
        evidence.distinctHostCount > 1 ||
        evidence.distinctExtractorFamilyCount > 1
    ) return _k0.ALL_AVAILABLE
    return _k0.ALL_AVAILABLE
}
