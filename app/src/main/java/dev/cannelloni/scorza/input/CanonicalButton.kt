package dev.cannelloni.scorza.input

// Moved to cannelloni-igm so the IGM input translator and the launcher share one enum.
// Kept as a typealias here so existing `dev.cannelloni.scorza.input.CanonicalButton` imports
// and `CanonicalButton.BTN_*` references continue to resolve unchanged.
typealias CanonicalButton = dev.cannelloni.igm.CanonicalButton
