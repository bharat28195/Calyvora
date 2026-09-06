"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Download, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { BankFileFormatT, BankFilePreview } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const FORMATS: { id: BankFileFormatT; label: string }[] = [
  { id: "GENERIC", label: "Generic CSV (for checking)" },
  { id: "HDFC", label: "HDFC bulk transfer" },
  { id: "ICICI", label: "ICICI bulk upload" },
  { id: "AXIS", label: "Axis bulk upload" },
];

const selectCls =
  "h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Download the month's salary transfer file.
 *
 * <p>The exclusions are shown <em>before</em> the download, on purpose. A missing IFSC discovered
 * here costs a minute; discovered by the bank after the batch is uploaded, it costs a re-run of
 * payday — banks reject the whole file, not the one bad row.
 */
export function BankFilePanel({ month, currency }: { month: string; currency: string }) {
  const [format, setFormat] = useState<BankFileFormatT>("HDFC");
  const [preview, setPreview] = useState<BankFilePreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setPreview(await api.bankFilePreview(month, format));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not check the bank details");
    }
  }, [month, format]);

  useEffect(() => {
    void load();
  }, [load]);

  async function download() {
    setBusy(true);
    setError(null);
    try {
      const blob = await api.downloadBankFile(month, format);
      // An object URL rather than a server link, because the request needs the auth header. Revoked
      // immediately after the click so the blob is not held for the life of the page.
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `salary-${month || "current"}-${format.toLowerCase()}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not build the bank file");
    } finally {
      setBusy(false);
    }
  }

  const money = (n: number) =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 }).format(n);

  return (
    <Card className="mt-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Pay everyone</CardTitle>
          <p className="mt-1 text-xs text-fg/50">
            A file you upload to corporate net banking, instead of typing each transfer by hand.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select
            aria-label="Bank format"
            className={selectCls}
            value={format}
            onChange={(e) => setFormat(e.target.value as BankFileFormatT)}
          >
            {FORMATS.map((f) => (
              <option key={f.id} value={f.id} className="bg-surface">{f.label}</option>
            ))}
          </select>
          <Button onClick={download} disabled={busy || preview?.payable === 0}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            Download
          </Button>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {preview && (
        <div className="mt-4 border-t border-fg/10 pt-4">
          <p className="text-sm">
            <span className="font-medium tabular-nums">{preview.payable}</span>
            <span className="text-fg/50"> {preview.payable === 1 ? "person" : "people"} · </span>
            <span className="font-medium tabular-nums">{money(preview.total)}</span>
            <span className="text-fg/50"> will leave your account</span>
          </p>

          {preview.excluded.length > 0 && (
            <div className="mt-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3">
              <p className="flex items-center gap-2 text-sm font-medium text-amber-200">
                <AlertTriangle className="h-4 w-4" />
                {preview.excluded.length} {preview.excluded.length === 1 ? "person is" : "people are"} not
                in this file
              </p>
              <ul className="mt-2 flex flex-col gap-1">
                {preview.excluded.map((e) => (
                  <li key={e.employeeId} className="text-xs text-amber-100/80">
                    <span className="font-medium">{e.name}</span> — {e.reason}
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-[11px] text-amber-100/60">
                Fix these on the employee&apos;s Finance tab, then download again. The total above is
                what the file actually pays, not the payroll total.
              </p>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
