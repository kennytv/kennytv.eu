'use client';

import { useState } from 'react';

export default function ConverterClient() {
  const [input, setInput] = useState('');
  const [output, setOutput] = useState('');
  const [copyStatus, setCopyStatus] = useState('');

  async function handleConvert() {
    try {
      const res = await fetch('https://item-converter.papermc.io/convert-command', {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: input,
      });
      const text = await res.text();
      setOutput(res.ok ? text : `Error: ${res.status} ${text}`);
    } catch (err) {
      setOutput(`Error: ${err}`);
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(output);
      setCopyStatus('Copied!');
      setTimeout(() => setCopyStatus(''), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  }

  return (
    <main className="min-h-screen">
      {/* Notice banner */}
      <div className="bg-red-700 py-3 px-4 text-center text-sm font-bold text-white">
        This converter has been moved to the{' '}
        <a
          href="https://docs.papermc.io/misc/tools/item-command-converter"
          target="_blank"
          rel="noopener"
          className="!text-white underline"
        >
          PaperMC site
        </a>
        .
      </div>

      <div className="container max-w-[980px] py-7 pb-12">
        <header className="mb-5">
          <h1 className="mb-2 text-2xl font-bold text-text">
            1.20.4 → 1.20.5 Command Converter
          </h1>
          <p className="text-text-muted">
            Paste a pre-1.20.5 command below. The output is the converted 1.20.5+ command.
          </p>
        </header>

        <section className="card p-5">
          <label htmlFor="input" className="mb-2 block text-left font-bold text-text">
            Input
          </label>
          <textarea
            id="input"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Enter <1.20.5 command here..."
            autoComplete="off"
            spellCheck={false}
            className="w-full min-h-[180px] resize-y rounded-lg border border-border bg-bg/50 p-3
              text-text placeholder:text-text-dim focus:border-primary/50 focus:outline-none transition-colors"
          />

          <div className="my-3 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleConvert}
              className="rounded-lg bg-green-600 px-4 py-2.5 font-bold text-white
                hover:bg-green-700 transition-colors"
            >
              Convert
            </button>
          </div>

          <label htmlFor="output" className="mb-2 mt-2 block text-left font-bold text-text">
            Output
          </label>
          <pre
            id="output"
            className="min-h-[180px] w-full overflow-auto whitespace-pre-wrap break-all rounded-lg
              border border-border bg-bg/30 p-3 text-text"
            aria-live="polite"
          >
            {output}
          </pre>

          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleCopy}
              className="rounded-lg bg-primary px-4 py-2.5 font-bold text-white
                hover:bg-primary-dark transition-colors"
            >
              Copy output
            </button>
            <span className="font-bold text-green-400" role="status" aria-live="polite">
              {copyStatus}
            </span>
          </div>
        </section>

        <footer className="mt-5 text-center text-text-muted">
          <p>
            Powered by{' '}
            <a href="https://github.com/PaperMC/DataConverter" target="_blank" rel="noopener">
              DataConverter
            </a>
          </p>
          <p className="mt-1 text-text-dim">Coughed up by Spottedleaf, jmp, kennytv.</p>
        </footer>
      </div>
    </main>
  );
}
