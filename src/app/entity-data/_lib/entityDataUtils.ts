/* ── Types ──────────────────────────────────────────── */

export interface EntityField {
  index: number;
  dataType: string;
  fieldName: string;
}

export interface EntityInfo {
  superClass: string | null;
  fields: EntityField[];
}

/** Map of entity name -> entity info for a single version */
export type VersionEntities = Record<string, EntityInfo>;

export interface EntityDataJson {
  versions: string[];
  data: Record<string, VersionEntities>;
}

/* ── Diff Types ────────────────────────────────────── */

export type FieldDiffStatus = 'unchanged' | 'added' | 'removed' | 'changed';

export interface DiffField {
  status: FieldDiffStatus;
  left?: EntityField;
  right?: EntityField;
}

export type EntityDiffStatus = 'unchanged' | 'added' | 'removed' | 'changed';

export interface EntityDiff {
  status: EntityDiffStatus;
  name: string;
  leftSuperClass?: string | null;
  rightSuperClass?: string | null;
  fields: DiffField[];
}

/* ── Search ────────────────────────────────────────── */

export function filterEntities(
  entities: VersionEntities,
  query: string,
): string[] {
  if (!query.trim()) {
    return Object.keys(entities);
  }
  const lower = query.toLowerCase();
  return Object.entries(entities)
    .filter(([name, info]) => {
      if (name.toLowerCase().includes(lower)) return true;
      return info.fields.some((f) => f.fieldName.toLowerCase().includes(lower));
    })
    .map(([name]) => name);
}

/* ── Inheritance Tree ──────────────────────────────── */

export interface TreeNode {
  name: string;
  children: TreeNode[];
}

export function buildTree(entities: VersionEntities): TreeNode[] {
  const childrenMap: Record<string, string[]> = {};
  const allNames = new Set(Object.keys(entities));

  for (const [name, info] of Object.entries(entities)) {
    const parent = info.superClass;
    if (parent && allNames.has(parent)) {
      if (!childrenMap[parent]) childrenMap[parent] = [];
      childrenMap[parent].push(name);
    }
  }

  // Sort children alphabetically
  for (const children of Object.values(childrenMap)) {
    children.sort();
  }

  function build(name: string): TreeNode {
    return {
      name,
      children: (childrenMap[name] ?? []).map(build),
    };
  }

  // Root nodes: entities whose superClass is not in the known set
  const roots = Object.entries(entities)
    .filter(([, info]) => !info.superClass || !allNames.has(info.superClass))
    .map(([name]) => name)
    .sort();

  return roots.map(build);
}

/* ── Navigation Helpers ────────────────────────────── */

/** Get the full ancestor chain for an entity: ['Entity', 'Living Entity', ..., name] */
export function getAncestorChain(
  entities: VersionEntities,
  name: string,
): string[] {
  const chain: string[] = [];
  let current: string | null = name;
  while (current && entities[current]) {
    chain.unshift(current);
    current = entities[current].superClass;
  }
  return chain;
}

/** Get sorted direct children of an entity */
export function getDirectChildren(
  entities: VersionEntities,
  parentName: string,
): string[] {
  return Object.entries(entities)
    .filter(([, info]) => info.superClass === parentName)
    .map(([name]) => name)
    .sort();
}

/** Check whether an entity has any children in the dataset */
export function hasChildren(
  entities: VersionEntities,
  name: string,
): boolean {
  return Object.values(entities).some((info) => info.superClass === name);
}

/* ── Inherited Fields ──────────────────────────────── */

export interface InheritedFieldGroup {
  entityName: string;
  fields: EntityField[];
}

/**
 * Collect all ancestor field groups for an entity (excluding its own fields).
 * Returns groups ordered from the root ancestor down to the direct parent,
 * each with the ancestor's own fields.
 */
export function getInheritedFieldGroups(
  entities: VersionEntities,
  name: string,
): InheritedFieldGroup[] {
  const chain = getAncestorChain(entities, name);
  // chain = ['Entity', ..., 'Mob', name] — drop the entity itself
  const ancestors = chain.slice(0, -1);
  return ancestors.map((ancestor) => ({
    entityName: ancestor,
    fields: entities[ancestor]?.fields ?? [],
  }));
}

/** Get the total field count for an entity including all ancestor fields */
export function getTotalFieldCount(
  entities: VersionEntities,
  name: string,
): number {
  let count = 0;
  let current: string | null = name;
  while (current && entities[current]) {
    count += entities[current].fields.length;
    current = entities[current].superClass;
  }
  return count;
}

/* ── Diff Computation ──────────────────────────────── */

export function computeDiff(
  left: VersionEntities,
  right: VersionEntities,
): EntityDiff[] {
  const allNames = new Set([...Object.keys(left), ...Object.keys(right)]);
  const diffs: EntityDiff[] = [];

  for (const name of Array.from(allNames).sort()) {
    const leftEntity = left[name];
    const rightEntity = right[name];

    if (!leftEntity) {
      // Entity added
      diffs.push({
        status: 'added',
        name,
        rightSuperClass: rightEntity.superClass,
        fields: rightEntity.fields.map((f) => ({ status: 'added', right: f })),
      });
    } else if (!rightEntity) {
      // Entity removed
      diffs.push({
        status: 'removed',
        name,
        leftSuperClass: leftEntity.superClass,
        fields: leftEntity.fields.map((f) => ({ status: 'removed', left: f })),
      });
    } else {
      // Both exist — diff fields
      const fields = diffFields(leftEntity.fields, rightEntity.fields);
      const hasChanges =
        fields.some((f) => f.status !== 'unchanged') ||
        leftEntity.superClass !== rightEntity.superClass;

      diffs.push({
        status: hasChanges ? 'changed' : 'unchanged',
        name,
        leftSuperClass: leftEntity.superClass,
        rightSuperClass: rightEntity.superClass,
        fields,
      });
    }
  }

  return diffs;
}

function diffFields(
  leftFields: EntityField[],
  rightFields: EntityField[],
): DiffField[] {
  const result: DiffField[] = [];

  // Match fields by fieldName
  const leftByName = new Map(leftFields.map((f) => [f.fieldName, f]));
  const rightByName = new Map(rightFields.map((f) => [f.fieldName, f]));
  const allFieldNames = new Set([...leftByName.keys(), ...rightByName.keys()]);

  for (const fieldName of allFieldNames) {
    const l = leftByName.get(fieldName);
    const r = rightByName.get(fieldName);

    if (!l) {
      result.push({ status: 'added', right: r });
    } else if (!r) {
      result.push({ status: 'removed', left: l });
    } else if (l.index !== r.index || l.dataType !== r.dataType) {
      result.push({ status: 'changed', left: l, right: r });
    } else {
      result.push({ status: 'unchanged', left: l, right: r });
    }
  }

  // Sort by index (use right index if available, otherwise left)
  result.sort((a, b) => {
    const aIdx = a.right?.index ?? a.left?.index ?? 0;
    const bIdx = b.right?.index ?? b.left?.index ?? 0;
    return aIdx - bIdx;
  });

  return result;
}

/** Filter diffs by search query */
export function filterDiffs(
  diffs: EntityDiff[],
  query: string,
): EntityDiff[] {
  if (!query.trim()) return diffs;
  const lower = query.toLowerCase();
  return diffs.filter((d) => {
    if (d.name.toLowerCase().includes(lower)) return true;
    return d.fields.some((f) => {
      const name = f.left?.fieldName ?? f.right?.fieldName ?? '';
      return name.toLowerCase().includes(lower);
    });
  });
}
