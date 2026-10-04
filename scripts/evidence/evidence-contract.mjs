import './check-runtime.mjs';
import { readFileSync } from 'node:fs';

export const schema = JSON.parse(readFileSync(new URL('../portfolio/evidence/evidence-v3.schema.json', import.meta.url), 'utf8'));
export const freshnessPolicies = JSON.parse(readFileSync(new URL('../portfolio/evidence/freshness-policies.json', import.meta.url), 'utf8')).policies;

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const isUtc = value => {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(value)) return false;
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().replace('.000Z', 'Z') === value.replace('.000Z', 'Z');
};
const isHttps = value => {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash;
  } catch { return false; }
};

// This dependency-free validator implements only the keywords used by our checked-in
// schema. Unknown keywords fail closed; this is not a general JSON Schema engine.
const supported = new Set(['$schema', '$id', '$defs', '$ref', 'title', 'description', 'type', 'const', 'enum', 'anyOf', 'required', 'properties', 'additionalProperties', 'items', 'minItems', 'uniqueItems', 'minimum', 'maximum', 'minLength', 'maxLength', 'pattern', 'format']);
function check(value, rule, path, errors) {
  for (const keyword of Object.keys(rule)) {
    if (!supported.has(keyword)) throw new Error(`Unsupported schema keyword ${keyword}`);
  }
  if (rule.$ref) {
    if (!rule.$ref.startsWith('#/$defs/')) throw new Error('Only local $defs references are supported');
    const resolved = schema.$defs[rule.$ref.slice(8)];
    if (!resolved) throw new Error(`Unresolved schema reference ${rule.$ref}`);
    check(value, resolved, path, errors);
    return;
  }
  if (rule.anyOf) {
    const branches = rule.anyOf.map(branch => { const found = []; check(value, branch, path, found); return found; });
    if (!branches.some(branch => branch.length === 0)) errors.push(`${path}: no permitted variant (${branches.map(branch => branch.join('; ')).join(' | ')})`);
    return;
  }
  const types = {
    null: value === null,
    object: value !== null && typeof value === 'object' && !Array.isArray(value),
    array: Array.isArray(value),
    integer: Number.isSafeInteger(value),
    number: typeof value === 'number' && Number.isFinite(value),
    string: typeof value === 'string',
    boolean: typeof value === 'boolean',
  };
  if (rule.type && !Object.hasOwn(types, rule.type)) throw new Error(`Unsupported schema type ${rule.type}`);
  if (rule.type && !types[rule.type]) { errors.push(`${path}: expected ${rule.type}`); return; }
  if (Object.hasOwn(rule, 'const') && !same(value, rule.const)) errors.push(`${path}: expected ${rule.const}`);
  if (rule.enum && !rule.enum.some(option => same(value, option))) errors.push(`${path}: unsupported enum value`);
  if (typeof value === 'number') {
    if (rule.minimum !== undefined && value < rule.minimum) errors.push(`${path}: below minimum`);
    if (rule.maximum !== undefined && value > rule.maximum) errors.push(`${path}: above maximum`);
  }
  if (typeof value === 'string') {
    if (rule.minLength !== undefined && value.length < rule.minLength) errors.push(`${path}: too short`);
    if (rule.maxLength !== undefined && value.length > rule.maxLength) errors.push(`${path}: too long`);
    if (rule.pattern && !new RegExp(rule.pattern).test(value)) errors.push(`${path}: invalid pattern`);
    if (rule.format === 'date-time' && !isUtc(value)) errors.push(`${path}: invalid UTC timestamp`);
    if (rule.format === 'https-url' && !isHttps(value)) errors.push(`${path}: expected HTTPS URL without credentials, query or fragment`);
    if (rule.format && !['date-time', 'https-url'].includes(rule.format)) throw new Error(`Unsupported schema format ${rule.format}`);
  }
  if (Array.isArray(value)) {
    if (rule.minItems !== undefined && value.length < rule.minItems) errors.push(`${path}: too few items`);
    if (rule.uniqueItems && new Set(value.map(item => JSON.stringify(item))).size !== value.length) errors.push(`${path}: duplicate items`);
    if (rule.items) value.forEach((item, index) => check(item, rule.items, `${path}[${index}]`, errors));
  }
  if (types.object) {
    for (const key of rule.required ?? []) if (!Object.hasOwn(value, key)) errors.push(`${path}.${key}: missing required property`);
    for (const [key, item] of Object.entries(value)) {
      if (Object.hasOwn(rule.properties ?? {}, key)) check(item, rule.properties[key], `${path}.${key}`, errors);
      else if (rule.additionalProperties === false) errors.push(`${path}.${key}: unexpected property`);
      else if (rule.additionalProperties && typeof rule.additionalProperties === 'object') check(item, rule.additionalProperties, `${path}.${key}`, errors);
    }
  }
}

export function validateEvidence(record) {
  const errors = [];
  check(record, schema, '$', errors);
  if (errors.length || record.kind === 'historical_attestation') return errors;
  const { execution: e, publication: p } = record;
  const c = e.counts;
  const fail = reason => errors.push(`semantic: ${reason}`);
  const runUrl = new URL(record.workflow.url);
  if (runUrl.hostname !== 'github.com' || runUrl.pathname !== `/${record.repository}/actions/runs/${record.workflow.runId}`) fail('workflow URL must identify this repository and immutable run ID');
  if (!Object.hasOwn(freshnessPolicies, record.scope.freshnessPolicy)) fail('unknown freshness policy');
  if (e.disposition !== 'passed' && !e.reason?.trim()) fail('non-passing execution requires a reason');
  if (['passed', 'failed', 'cancelled'].includes(e.disposition) && !e.completedAt) fail('terminal execution requires completedAt');
  if (['passed', 'failed', 'in_progress', 'cancelled'].includes(e.disposition) && !e.startedAt) fail('attempted execution requires startedAt');
  if (e.disposition === 'in_progress' && e.completedAt !== null) fail('in-progress execution cannot be completed');
  if (e.startedAt && e.completedAt && Date.parse(e.completedAt) < Date.parse(e.startedAt)) fail('completion precedes start');
  const expected = new Set(e.shards.expected);
  if (e.shards.received.some(shard => !expected.has(shard))) fail('unexpected shard');
  const allShards = e.shards.received.length === expected.size;
  if (e.integrity === 'complete' && !allShards) fail('complete integrity requires every expected shard');
  if (c.unit === 'not_applicable' && ['selected', 'executed', 'passed', 'failed', 'skipped', 'retried'].some(key => c[key] !== null)) fail('non-test scope must not invent test counts');
  if (c.passed !== null && c.failed !== null && c.executed !== null && c.passed + c.failed > c.executed) fail('final outcomes exceed execution count');
  if (c.selected !== null && c.executed !== null && c.skipped !== null && c.executed + c.skipped > c.selected) fail('execution plus skip count exceeds selected count');
  if (c.retried !== null && c.executed !== null && c.retried > c.executed) fail('retried unique cases exceed executed unique cases');
  if (['skipped', 'unavailable'].includes(e.disposition) && (['executed', 'passed', 'failed', 'retried'].some(key => c[key] > 0) || e.measurements.length)) fail('non-execution disposition cannot claim substantive execution');
  if (e.integrity === 'empty' && (c.executed > 0 || e.measurements.length)) fail('empty evidence cannot contain substantive execution');
  if (e.disposition === 'passed') {
    if (e.integrity !== 'complete' || !allShards) fail('pass requires complete evidence and all expected shards');
    if (!(c.executed > 0 || e.measurements.length > 0)) fail('pass requires substantive native execution');
    if (c.failed > 0) fail('pass cannot contain failed final cases');
    if (c.executed !== null && c.passed !== null && c.failed !== null && c.executed !== c.passed + c.failed) fail('pass must reconcile final case outcomes');
    if (c.selected !== null && c.executed !== null && c.skipped !== null && c.selected !== c.executed + c.skipped) fail('pass must reconcile selected cases');
  }
  if (new Set(e.measurements.map(item => item.name)).size !== e.measurements.length) fail('duplicate measurement identity');
  if (new Set(p.artifacts.map(item => item.name)).size !== p.artifacts.length) fail('duplicate artifact identity');
  for (const artifact of p.artifacts) {
    if (artifact.retention.class === 'ephemeral' && !artifact.retention.expiresAt) fail('ephemeral artifact requires expiry');
    if (artifact.retention.class === 'durable' && artifact.retention.expiresAt !== null) fail('durable artifact must use its policy rather than ephemeral expiry');
  }
  if (p.disposition === 'published' && (!p.publishedAt || !p.artifacts.length)) fail('published evidence requires time and checksummed artifacts');
  if (p.disposition !== 'published' && (p.publishedAt !== null || p.reportUrl !== null)) fail('unpublished record cannot claim current publication time/report');
  if (['failed', 'not_applicable'].includes(p.disposition) && !p.reason?.trim()) fail('publication disposition requires a reason');
  if (p.publishedAt && e.completedAt && Date.parse(p.publishedAt) < Date.parse(e.completedAt)) fail('publication precedes execution completion');
  return errors;
}

export function evidenceFreshness(record, now) {
  const errors = validateEvidence(record);
  if (errors.length) return { state: 'invalid', reasons: errors };
  if (!Number.isFinite(now)) throw new Error('A finite controlled clock is required');
  if (record.kind === 'historical_attestation') return { state: 'historical', reasons: ['Reviewed attestation is not current execution'] };
  const e = record.execution;
  if (e.disposition !== 'passed') return { state: e.disposition, reasons: [e.reason] };
  const policy = freshnessPolicies[record.scope.freshnessPolicy];
  if (policy.maxAgeHours === null) return { state: 'historical', reasons: [policy.rationale] };
  const ageHours = (now - Date.parse(e.completedAt)) / 3_600_000;
  if (ageHours < 0) return { state: 'invalid', reasons: ['Execution completion is in the future'] };
  return { state: ageHours <= policy.maxAgeHours ? 'fresh' : 'stale', reasons: [policy.rationale], ageHours };
}

export function classifyLegacyAttestation(record) {
  // Preserve old bytes/fields; do not fabricate v3 counts, target or run attempts.
  return { kind: 'legacy_attestation', eligibleAsCurrentExecution: false, record,
    limitations: ['Legacy record requires an E02 native emitter; manual run/SHA attestations remain historical'] };
}
