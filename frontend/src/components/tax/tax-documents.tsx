"use client";

import { useState } from "react";
import Link from "next/link";
import { Calculator, FileCheck2, FileText } from "lucide-react";
import { Card } from "@/components/ui/card";
import { recentTaxYears, taxYearOf, yearQuery } from "@/lib/tax-years";
import { cn } from "@/lib/utils";

/**
 * The employee's tax papers in one place, for this year or a recent one: Form 130 (formerly Form 16)
 * to file a return with, Form 124 (formerly 12BB) as declared, and the computation behind both.
 *
 * <p>Form 130 is what people come looking for every June, for the year just ended — so the year is
 * chosen here, and last year is one click away rather than buried behind the current declaration.
 */
export function TaxDocuments({ className }: { className?: string }) {
  const [year, setYear] = useState(taxYearOf());
  const q = yearQuery(year);
  const docs = [
    { href: `/finance/tax/form130${q}`, icon: FileCheck2, title: "Form 130", sub: "Formerly Form 16 · for your tax return" },
    { href: `/finance/tax/form124${q}`, icon: FileText, title: "Form 124", sub: "Formerly 12BB · what you declared" },
    { href: `/finance/tax/computation${q}`, icon: Calculator, title: "How it's calculated", sub: "Every rupee, month by month" },
  ];
  return (
    <Card className={cn("p-4", className)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold">Your tax documents</p>
        <select value={year} onChange={(e) => setYear(e.target.value)} aria-label="Tax year"
          className="rounded-lg border border-fg/15 bg-fg/5 px-2 py-1 text-xs text-fg">
          {recentTaxYears(4).map((y) => <option key={y} value={y} className="bg-surface">Tax year {y}</option>)}
        </select>
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        {docs.map((d) => (
          <Link key={d.title} href={d.href}
            className="flex items-start gap-2.5 rounded-lg border border-fg/10 px-3 py-2.5 transition-colors hover:border-violet/40 hover:bg-violet/5">
            <d.icon className="mt-0.5 h-4 w-4 shrink-0 text-violet" />
            <span>
              <span className="block text-sm font-medium">{d.title}</span>
              <span className="block text-xs text-fg/50">{d.sub}</span>
            </span>
          </Link>
        ))}
      </div>
    </Card>
  );
}
