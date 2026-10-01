package com.calyvora.tax;

/**
 * Which set of rules an employee's income tax is worked out under.
 *
 * <p>India has run two in parallel since 2020. The <b>new</b> regime has wider slabs and a much
 * larger rebate, but disallows almost every deduction; the <b>old</b> regime taxes more steeply and
 * lets people subtract what they have invested and spent. Neither is better in general — which one
 * wins depends entirely on how much a particular person has to deduct — which is why the choice
 * belongs to the employee rather than to a company-wide setting.
 *
 * <p>The new regime is the statutory default: somebody who never declares anything is taxed under
 * it, which is also the arrangement that needs no paperwork from them.
 */
public enum TaxRegime {
    OLD,
    NEW;

    /** What an employee is taxed under until they say otherwise. */
    public static final TaxRegime DEFAULT = NEW;
}
