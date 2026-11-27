# Insulin curves: what the sources say (autoISF build)

This is a short reading aid, not a setting. It comes from the JugglucoNG document
`docs/insulin-curve-evidence.md` (github.com/ctqvva/JugglucoNG, GPL-3.0), which reads glucose
infusion rate (GIR) curves from clamp studies in the official product information. Read the
original for every source link and the method.

## Why it matters for AAPS

AAPS models the action of a rapid insulin with one exponential curve and one number: the peak
time (Rapid-Acting Oref 75 min, Ultra-Rapid Oref 55 min, Lyumjev 45 min, or Free-Peak). The
curves below are population averages from clamp studies at fixed doses. They are not personal and
not a clinical check of AAPS. Use them only to ask "is my peak setting far from what the studies
saw?", then test any change carefully with your own data.

## Rapid insulins

| Insulin | What the source has | Peak in the source | End of the observed curve |
| --- | --- | --- | --- |
| Fiasp (aspart) | GIR at 0.1, 0.2 and 0.4 U/kg | about 91 min | about 5 h |
| Lyumjev (lispro-aabc) | GIR at 7, 15 and 30 U | about 120 min | about 276 min |
| NovoRapid / NovoLog (aspart) | no complete GIR curve in the label | - | - |
| Humalog (lispro) | meal data only, no complete GIR curve | - | - |
| Apidra (glulisine) | only an obese subgroup GIR plot | - | - |
| Afrezza (inhaled) | GIR at 4, 12 and 48 U | about 45 min (12 U) | about 180 min |
| Humulin R / Novolin R (regular) | GIR at 0.3 U/kg | about 180 min | observed to 14 h |

Notes from the source:

- The onset of Fiasp in its GIR figure is about 5 min, earlier than the 16 to 20 min "first
  measurable effect" in the label.
- Bigger doses act longer. Where several doses were studied, the curves differ.
- Activity curves (GIR) and plasma insulin curves do not have the same timing. AAPS needs activity.

## Basal and mixed insulins

These do not matter for a pump. Most of their studies end while insulin is still acting, or
measure many daily doses together (steady state), so they do not give the action of one dose.
