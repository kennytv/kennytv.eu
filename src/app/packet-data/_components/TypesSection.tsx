'use client';

import { useEffect, useState } from 'react';
import type { PacketNode } from '../_lib/packetDataUtils';
import { typeAnchor } from '../_lib/packetDataUtils';
import NodeTree from './NodeTree';

interface TypesSectionProps {
  types: Record<string, PacketNode>;
  onTypeClick?: (typeName: string) => void;
  /** Type name that should be expanded (set when a ref link is clicked). */
  openType?: string | null;
  /** Lowercased search query — cards open and matches are highlighted while searching. */
  highlight?: string;
}

/** Ordered category matchers — the first match wins, so specific ones come first. */
const TYPE_CATEGORIES: { label: string; test: (name: string) => boolean }[] = [
  { label: 'Packet-specific', test: (n) => /Packet/.test(n) },
  { label: 'Debug', test: (n) => /^Debug/.test(n) },
  { label: 'Particles', test: (n) => /Particle|^Dust|^ExplosionParticle/.test(n) },
  { label: 'Recipes & slots', test: (n) => /Recipe|SlotDisplay|Ingredient|^HashedStack/.test(n) },
  {
    label: 'Text, chat & dialogs',
    test: (n) => /^(ChatType|NumberFormat|FixedFormat|StyledFormat|Dialog|ServerLinks)/.test(n),
  },
  {
    label: 'Items & data components',
    test: (n) =>
      /^(ItemStack|ItemStackTemplate|TypedDataComponent|DataComponent|HashedPatchMap|ItemAttributeModifiers|ItemEnchantments|AttributeModifier|AdventureModePredicate|BlockPredicate|StatePropertiesPredicate|BlockItemStateProperties|Consumable|ConsumeEffect|.*ConsumeEffect$|Tool|Weapon|KineticWeapon|PiercingWeapon|AttackRange|BlocksAttacks|Equippable|Enchantable|Repairable|DamageResistant|DeathProtection|FoodProperties|UseCooldown|UseEffects|UseRemainder|SwingAnimation|Firework|PotionContents|SuspiciousStewEffects|OminousBottleAmplifier|WritableBookContent|WrittenBookContent|DyedItemColor|CustomModelData|TooltipDisplay|LodestoneTracker|Jukebox|Instrument|BannerPattern|ArmorTrim|TrimMaterial|TrimPattern|MaterialAssetGroup|MobEffectInstance|MapDecoration)/.test(
        n,
      ),
  },
  {
    label: 'Players & profiles',
    test: (n) => /^(Game Profile|ResolvableProfile|PlayerSkin)/.test(n),
  },
  { label: 'Everything else', test: () => true },
];

function categorize(typeNames: string[]): { label: string; names: string[] }[] {
  const groups = TYPE_CATEGORIES.map((c) => ({ label: c.label, names: [] as string[] }));
  for (const name of typeNames) {
    groups[TYPE_CATEGORIES.findIndex((c) => c.test(name))].names.push(name);
  }
  return groups.filter((g) => g.names.length > 0);
}

export default function TypesSection({ types, onTypeClick, openType, highlight }: TypesSectionProps) {
  const typeNames = Object.keys(types).sort((a, b) => a.localeCompare(b));

  if (typeNames.length === 0) return null;

  return (
    <div className="mt-8">
      <h2 className="mb-1 text-lg font-semibold text-text">Types</h2>
      <p className="mb-4 text-sm text-text-dim">
        Shared structures referenced by packet fields above.
      </p>
      <div className="space-y-6">
        {categorize(typeNames).map(({ label, names }) => (
          <div key={label}>
            <h3 className="mb-3 text-sm font-medium text-text-dim uppercase tracking-wider">
              {label}
              <span className="ml-2 text-text-muted font-normal normal-case tracking-normal">
                ({names.length})
              </span>
            </h3>
            <div className="space-y-3">
              {names.map((name) => (
                <TypeCard
                  key={name}
                  name={name}
                  node={types[name]}
                  onTypeClick={onTypeClick}
                  forceOpen={openType === name}
                  searchOpen={!!highlight}
                  highlight={highlight}
                />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function TypeCard({
  name,
  node,
  onTypeClick,
  forceOpen,
  searchOpen,
  highlight,
}: {
  name: string;
  node: PacketNode;
  onTypeClick?: (typeName: string) => void;
  forceOpen?: boolean;
  /** Open while a search is active, so the (highlighted) match is visible. */
  searchOpen?: boolean;
  highlight?: string;
}) {
  const [open, setOpen] = useState(false);
  const shown = open || !!searchOpen;

  useEffect(() => {
    if (forceOpen) setOpen(true);
  }, [forceOpen]);

  return (
    <div id={typeAnchor(name)} className="card scroll-mt-4 p-4 md:p-6">
      <div className="group flex w-full items-center gap-2">
        <button
          type="button"
          onClick={() => setOpen(!shown)}
          className="flex min-w-0 items-center gap-2 text-left"
        >
          <span className="text-xs text-text-dim">{shown ? '▾' : '▸'}</span>
          <h4 className="text-base font-semibold text-text">{name}</h4>
        </button>
        <a
          href={`#${typeAnchor(name)}`}
          aria-label="Link to this type"
          className="text-text-dim opacity-0 transition-opacity group-hover:opacity-100 hover:text-primary"
        >
          #
        </a>
      </div>
      {shown && (
        <div className="mt-3">
          <NodeTree node={node} onTypeClick={onTypeClick} highlight={highlight} />
        </div>
      )}
    </div>
  );
}
