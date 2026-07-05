'use client';

import { createContext, useContext, useState, type ReactNode } from 'react';
import type { PacketNode, PacketVariant } from '../_lib/packetDataUtils';
import { shortRegistry, WIRE_ENCODINGS, CONTAINER_ENCODINGS } from '../_lib/packetDataUtils';

/** Active search query (lowercased) — matching text in the tree is highlighted. */
const HighlightContext = createContext('');

function Highlighted({ text }: { text: string }) {
  const query = useContext(HighlightContext);
  const idx = query ? text.toLowerCase().indexOf(query) : -1;
  if (idx < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, idx)}
      <mark className="rounded-sm bg-amber/30 text-inherit">{text.slice(idx, idx + query.length)}</mark>
      {text.slice(idx + query.length)}
    </>
  );
}

/** Wire/container label with an encoding tooltip when one is known. */
function Encoded({ label, className, children }: { label: string; className: string; children?: ReactNode }) {
  const description = WIRE_ENCODINGS[label] ?? CONTAINER_ENCODINGS[label];
  return (
    <span
      className={`${className}${description ? ' cursor-help underline decoration-dotted decoration-current/40 underline-offset-2' : ''}`}
      title={description}
    >
      {children ?? <Highlighted text={label} />}
    </span>
  );
}

interface NodeTreeProps {
  node: PacketNode;
  onTypeClick?: (typeName: string) => void;
  /** Lowercased search query; occurrences in names/types are highlighted. */
  highlight?: string;
}

/** Renders a packet body (or type definition) as an indented wire-format tree. */
export default function NodeTree({ node, onTypeClick, highlight }: NodeTreeProps) {
  return (
    <HighlightContext.Provider value={highlight ?? ''}>
      <NodeTreeBody node={node} onTypeClick={onTypeClick} />
    </HighlightContext.Provider>
  );
}

function NodeTreeBody({ node, onTypeClick }: { node: PacketNode; onTypeClick?: (typeName: string) => void }) {
  if (node.kind === 'unit') {
    return <p className="text-sm text-text-dim">Empty packet — no fields.</p>;
  }
  if (node.kind === 'opaque' && !node.fields?.length) {
    return (
      <p className="text-sm text-text-dim italic">
        Could not be extracted{node.note ? ` (${node.note})` : ''}.
      </p>
    );
  }

  // Roots that are pure containers render their children directly.
  if ((node.kind === 'container' || node.kind === 'traced' || node.kind === 'opaque') && node.fields) {
    return (
      <div className="font-mono text-sm">
        {node.partial && (
          <p className="mb-1 text-xs italic text-text-dim font-sans">
            Partial — decoding failed midway{node.note ? ` (${node.note})` : ''}.
          </p>
        )}
        {node.fields.map((child, i) => (
          <NodeRow key={i} node={child} onTypeClick={onTypeClick} />
        ))}
      </div>
    );
  }

  return (
    <div className="font-mono text-sm">
      <NodeRow node={node} onTypeClick={onTypeClick} />
    </div>
  );
}

/* ── inline rendering of simple types ────────────────── */

/** Can this node be shown as a single compact type expression? */
function isInline(node: PacketNode): boolean {
  switch (node.kind) {
    case 'value':
    case 'ref':
    case 'registry':
    case 'holderSet':
    case 'enum':
    case 'unit':
      return true;
    case 'opaque':
      return !node.fields?.length;
    case 'list':
      return node.elem != null && isInline(node.elem);
    case 'optional':
    case 'prefixed':
      return node.inner != null && isInline(node.inner);
    case 'map':
      return node.key != null && node.value != null && isInline(node.key) && isInline(node.value);
    case 'either':
      return node.left != null && node.right != null && isInline(node.left) && isInline(node.right);
    case 'holder':
      return node.direct == null || isInline(node.direct);
    default:
      return false;
  }
}

function InlineType({ node, onTypeClick }: { node: PacketNode; onTypeClick?: (t: string) => void }) {
  const wrap = (label: string, children: ReactNode) => (
    <>
      <Encoded label={label} className="text-accent-light">
        <Highlighted text={label} />
        &lt;
      </Encoded>
      {children}
      <span className="text-accent-light">&gt;</span>
    </>
  );

  switch (node.kind) {
    case 'value':
      return <Encoded label={node.wire ?? ''} className="text-amber" />;
    case 'ref':
      return (
        <button
          type="button"
          onClick={() => onTypeClick?.(node.ref!)}
          className="text-primary hover:text-primary-light transition-colors underline decoration-primary/40 underline-offset-2"
        >
          <Highlighted text={node.ref!} />
        </button>
      );
    case 'registry':
      return (
        <span className="text-amber">
          VarInt<span className="text-text-dim"> id of </span>
          <span className="text-primary-light">{shortRegistry(node.registry)}</span>
        </span>
      );
    case 'holderSet':
      return (
        <span className="text-amber">
          VarInt
          <span className="text-text-dim">
            {' '}— 0 = tag <span className="text-amber">Identifier</span> follows, else (count + 1), then that many{' '}
            <span className="text-primary-light">{shortRegistry(node.registry)}</span> ids
          </span>
        </span>
      );
    case 'enum':
      return (
        <span className="text-amber">
          <Encoded label={node.wire ?? 'VarInt'} className="text-amber" />
          {node.java && <span className="text-text-dim"> enum {node.java}</span>}
        </span>
      );
    case 'unit':
      return <span className="text-text-dim italic">nothing</span>;
    case 'opaque':
      return <span className="text-text-dim italic">unknown{node.note ? ` (${node.note})` : ''}</span>;
    case 'list':
      return wrap('List', <InlineType node={node.elem!} onTypeClick={onTypeClick} />);
    case 'optional':
      return wrap('Optional', <InlineType node={node.inner!} onTypeClick={onTypeClick} />);
    case 'prefixed':
      return (
        <>
          {wrap('Prefixed', <InlineType node={node.inner!} onTypeClick={onTypeClick} />)}
          <span className="text-text-dim text-xs"> (VarInt byte length first)</span>
        </>
      );
    case 'map':
      return wrap(
        'Map',
        <>
          <InlineType node={node.key!} onTypeClick={onTypeClick} />
          <span className="text-accent-light">, </span>
          <InlineType node={node.value!} onTypeClick={onTypeClick} />
        </>,
      );
    case 'either':
      return (
        <>
          {wrap(
            'Either',
            <>
              <InlineType node={node.left!} onTypeClick={onTypeClick} />
              <span className="text-accent-light">, </span>
              <InlineType node={node.right!} onTypeClick={onTypeClick} />
            </>,
          )}
          <span className="text-text-dim text-xs"> (Boolean: true = first)</span>
        </>
      );
    case 'holder':
      return (
        <span className="text-amber">
          VarInt
          <span className="text-text-dim">
            {' '}—{' '}
            {node.direct ? (
              <>
                0 = inline <InlineType node={node.direct} onTypeClick={onTypeClick} /> follows, else{' '}
                <span className="text-primary-light">{shortRegistry(node.registry)}</span> id + 1
              </>
            ) : (
              <>
                <span className="text-primary-light">{shortRegistry(node.registry)}</span> id
              </>
            )}
          </span>
        </span>
      );
    default:
      return <span className="text-text-dim">{node.kind}</span>;
  }
}

/* ── block rendering ─────────────────────────────────── */

interface NodeRowProps {
  node: PacketNode;
  label?: string;
  onTypeClick?: (typeName: string) => void;
}

function Indented({ children }: { children: ReactNode }) {
  return <div className="ml-2 border-l border-border/70 pl-3">{children}</div>;
}

function RowShell({
  name,
  label,
  type,
  node,
}: {
  name?: string;
  label?: string;
  type: ReactNode;
  node: PacketNode;
}) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 py-1">
      {label && !name && <span className="text-text-dim italic font-sans text-xs">{label}</span>}
      {name && (
        <span className="text-text">
          <Highlighted text={name} />
        </span>
      )}
      {/* single flex item so gaps never appear inside the type expression */}
      <span>{type}</span>
      {node.x != null && <span className="text-amber">×{node.x}</span>}
      {node.limit != null && <span className="text-xs text-text-muted">max {node.limit}</span>}
      {node.java && node.java !== node.wire && node.java !== 'Optional' && node.java !== 'Map' && node.java !== 'List' && (
        <span className="text-xs text-text-muted">
          <Highlighted text={node.java} />
        </span>
      )}
      {node.note && <span className="text-xs italic text-text-dim font-sans">{node.note}</span>}
      {node.link && (
        <a
          href={node.link}
          className="text-xs text-primary underline decoration-primary/40 underline-offset-2 hover:text-primary-light font-sans"
        >
          {node.linkText ?? 'details'}
        </a>
      )}
      {node.values && <EnumValues values={node.values} />}
    </div>
  );
}

function NodeRow({ node, label, onTypeClick }: NodeRowProps) {
  const name = node.name ?? undefined;

  // Compact single-row rendering when the whole subtree is simple
  if (isInline(node)) {
    return <RowShell name={name} label={label} type={<InlineType node={node} onTypeClick={onTypeClick} />} node={node} />;
  }

  switch (node.kind) {
    case 'container':
    case 'traced': {
      return (
        <div>
          <RowShell
            name={name}
            label={label}
            type={<span className="text-text-dim">{node.java ? <Highlighted text={node.java} /> : ''}</span>}
            node={node}
          />
          <Indented>
            {node.fields?.map((child, i) => (
              <NodeRow key={i} node={child} onTypeClick={onTypeClick} />
            ))}
          </Indented>
        </div>
      );
    }
    case 'group': {
      return (
        <div>
          <RowShell
            name={name}
            label={label}
            type={
              <span className="text-text-dim">
                <Highlighted text={prettyContext(node.context)} />
              </span>
            }
            node={node}
          />
          <Indented>
            {node.fields?.map((child, i) => (
              <NodeRow key={i} node={child} onTypeClick={onTypeClick} />
            ))}
          </Indented>
        </div>
      );
    }
    case 'list':
      return <WrapperRow node={node} name={name} label={label} kindLabel="List" childLabel="element" child={node.elem!} onTypeClick={onTypeClick} />;
    case 'optional':
      return <WrapperRow node={node} name={name} label={label} kindLabel="Optional" childLabel="value if present" child={node.inner!} onTypeClick={onTypeClick} />;
    case 'prefixed':
      return <WrapperRow node={node} name={name} label={label} kindLabel="Length-prefixed" childLabel="content" child={node.inner!} onTypeClick={onTypeClick} />;
    case 'map': {
      return (
        <div>
          <RowShell name={name} label={label} type={<Encoded label="Map" className="text-accent-light" />} node={node} />
          <Indented>
            {node.key && <NodeRow node={node.key} label="key" onTypeClick={onTypeClick} />}
            {node.value && <NodeRow node={node.value} label="value" onTypeClick={onTypeClick} />}
          </Indented>
        </div>
      );
    }
    case 'either': {
      return (
        <div>
          <RowShell
            name={name}
            label={label}
            type={
              <span className="text-accent-light">
                <Encoded label="Either" className="text-accent-light" />{' '}
                <span className="text-text-dim text-xs font-sans">(Boolean chooses)</span>
              </span>
            }
            node={node}
          />
          <Indented>
            {node.left && <NodeRow node={node.left} label="if true" onTypeClick={onTypeClick} />}
            {node.right && <NodeRow node={node.right} label="if false" onTypeClick={onTypeClick} />}
          </Indented>
        </div>
      );
    }
    case 'holder': {
      return (
        <div>
          <RowShell
            name={name}
            label={label}
            type={
              <span className="text-amber">
                VarInt
                <span className="text-text-dim">
                  {' '}— 0 = inline value follows, else{' '}
                  <span className="text-primary-light">{shortRegistry(node.registry)}</span> id + 1
                </span>
              </span>
            }
            node={node}
          />
          {node.direct && (
            <Indented>
              <NodeRow node={node.direct} label="inline value when 0" onTypeClick={onTypeClick} />
            </Indented>
          )}
        </div>
      );
    }
    case 'dispatch': {
      return (
        <div>
          <RowShell name={name} label={label} type={<Encoded label="Dispatch" className="text-accent-light" />} node={node} />
          <Indented>
            {node.key && <NodeRow node={node.key} label="type" onTypeClick={onTypeClick} />}
            {node.variants && <Variants variants={node.variants} onTypeClick={onTypeClick} />}
          </Indented>
        </div>
      );
    }
    case 'opaque': {
      return (
        <div>
          <RowShell name={name} label={label} type={<span className="text-text-dim italic">partial</span>} node={node} />
          <Indented>
            {node.fields?.map((child, i) => (
              <NodeRow key={i} node={child} onTypeClick={onTypeClick} />
            ))}
          </Indented>
        </div>
      );
    }
    default:
      return <RowShell name={name} label={label} type={<span className="text-text-dim">{node.kind}</span>} node={node} />;
  }
}

function WrapperRow({
  node,
  name,
  label,
  kindLabel,
  childLabel,
  child,
  onTypeClick,
}: {
  node: PacketNode;
  name?: string;
  label?: string;
  kindLabel: string;
  childLabel: string;
  child: PacketNode;
  onTypeClick?: (t: string) => void;
}) {
  return (
    <div>
      <RowShell name={name} label={label} type={<Encoded label={kindLabel} className="text-accent-light" />} node={node} />
      <Indented>
        <NodeRow node={child} label={childLabel} onTypeClick={onTypeClick} />
      </Indented>
    </div>
  );
}

function prettyContext(context?: string): string {
  if (!context) return '';
  const simple = context.replace(/\$\d+$/, '');
  const idx = simple.lastIndexOf('$');
  return idx >= 0 ? simple.slice(idx + 1) : simple;
}

function EnumValues({ values }: { values: string[] }) {
  const [expanded, setExpanded] = useState(false);
  const shown = expanded ? values : values.slice(0, 6);
  return (
    <span className="text-xs text-text-muted">
      {'{'}
      {shown.join(', ')}
      {values.length > 6 && !expanded && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="ml-1 text-primary hover:text-primary-light"
        >
          … +{values.length - 6} more
        </button>
      )}
      {'}'}
    </span>
  );
}

function Variants({
  variants,
  onTypeClick,
}: {
  variants: PacketVariant[];
  onTypeClick?: (t: string) => void;
}) {
  const [open, setOpen] = useState(variants.length <= 8);

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="py-1 text-xs text-primary hover:text-primary-light transition-colors font-sans"
      >
        {open ? '▾' : '▸'} {variants.length} {variants.length === 1 ? 'variant' : 'variants'}
      </button>
      {open && (
        <div className="space-y-1">
          {variants.map((v) => (
            <div key={v.key} className="rounded-md border border-border/50 bg-bg-surface/50 px-2 py-1">
              <div className="text-xs font-medium text-primary-light">
                <Highlighted text={v.key} />
              </div>
              <VariantBody body={v.body} onTypeClick={onTypeClick} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function VariantBody({ body, onTypeClick }: { body: PacketNode; onTypeClick?: (t: string) => void }) {
  if (body.kind === 'unit' || (body.fields && body.fields.length === 0)) {
    return <div className="text-xs text-text-dim italic font-sans">no fields</div>;
  }
  if ((body.kind === 'container' || body.kind === 'traced') && body.fields) {
    return (
      <div>
        {body.fields.map((child, i) => (
          <NodeRow key={i} node={child} onTypeClick={onTypeClick} />
        ))}
      </div>
    );
  }
  return <NodeRow node={body} onTypeClick={onTypeClick} />;
}
