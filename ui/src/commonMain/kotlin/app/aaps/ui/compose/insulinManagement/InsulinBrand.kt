package app.aaps.ui.compose.insulinManagement

import app.aaps.core.interfaces.insulin.InsulinType

/**
 * autoISF build: an insulin by its trade name, as the user knows it from JugglucoNG, on top of the AAPS template whose
 * curve the algorithm uses for it.
 *
 * Only the name comes from JugglucoNG. Its curves are clinical GIR curves of one injection (Fiasp peaks there at about
 * 91 minutes, Lyumjev at about 120). The oref model of AAPS is fitted to looping and puts these peaks much earlier
 * (55 and 45 minutes). Taking the later peaks would make the loop think the insulin acts later, and give more insulin.
 * So each name gets the peak of the AAPS template, and the user can still change peak and DIA in the editor.
 *
 * The names are trade names, the same in every language, so they are not string resources.
 */
data class InsulinBrand(val name: String, val template: InsulinType)

/** Lyumjev is not here: it is an AAPS template of its own already. */
val INSULIN_BRANDS = listOf(
    InsulinBrand("NovoRapid", InsulinType.OREF_RAPID_ACTING),
    InsulinBrand("Humalog", InsulinType.OREF_RAPID_ACTING),
    InsulinBrand("Apidra", InsulinType.OREF_RAPID_ACTING),
    InsulinBrand("Fiasp", InsulinType.OREF_ULTRA_RAPID_ACTING)
)
