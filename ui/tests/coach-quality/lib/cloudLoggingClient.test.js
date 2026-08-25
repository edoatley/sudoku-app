import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchLogLinesGcp, isRemoteLogSource, resolveProject, resolveServiceName } from './cloudLoggingClient.js';

// Stable mock instance shared with the module under test (hoisted above the import).
const { execFileSync } = vi.hoisted(() => ({ execFileSync: vi.fn() }));
vi.mock('node:child_process', () => ({ default: { execFileSync }, execFileSync }));

const REMOTE_URL = 'https://sudoku-rcg-vertex-validate-6cxgoss43q-uc.a.run.app/api/v1';
const ENV_KEYS = ['COACH_QUALITY_API_URL', 'COACH_QUALITY_GCP_SERVICE', 'COACH_QUALITY_GCP_PROJECT'];

let savedEnv;

beforeEach(() => {
  savedEnv = {};
  for (const k of ENV_KEYS) savedEnv[k] = process.env[k];
  for (const k of ENV_KEYS) delete process.env[k];
  vi.mocked(execFileSync).mockReset();
});

afterEach(() => {
  for (const k of ENV_KEYS) {
    if (savedEnv[k] === undefined) delete process.env[k];
    else process.env[k] = savedEnv[k];
  }
});

describe('isRemoteLogSource', () => {
  it('is false when COACH_QUALITY_API_URL is unset', () => {
    expect(isRemoteLogSource()).toBe(false);
  });

  it('is false for a local host (docker stack)', () => {
    process.env.COACH_QUALITY_API_URL = 'http://localhost:8080/api/v1';
    expect(isRemoteLogSource()).toBe(false);
    process.env.COACH_QUALITY_API_URL = 'http://127.0.0.1:8080/api/v1';
    expect(isRemoteLogSource()).toBe(false);
  });

  it('is true for a deployed Cloud Run host', () => {
    process.env.COACH_QUALITY_API_URL = REMOTE_URL;
    expect(isRemoteLogSource()).toBe(true);
  });

  it('is false for a malformed URL', () => {
    process.env.COACH_QUALITY_API_URL = 'not a url';
    expect(isRemoteLogSource()).toBe(false);
  });
});

describe('resolveServiceName', () => {
  it('prefers the explicit COACH_QUALITY_GCP_SERVICE override', () => {
    process.env.COACH_QUALITY_API_URL = REMOTE_URL;
    process.env.COACH_QUALITY_GCP_SERVICE = 'custom-service';
    expect(resolveServiceName()).toBe('custom-service');
  });

  it('derives the service name from the Cloud Run default hostname', () => {
    process.env.COACH_QUALITY_API_URL = REMOTE_URL;
    expect(resolveServiceName()).toBe('sudoku-rcg-vertex-validate');
  });

  it('throws when the host is not a derivable Cloud Run hostname', () => {
    process.env.COACH_QUALITY_API_URL = 'https://coach.example.com/api/v1';
    expect(() => resolveServiceName()).toThrow(/COACH_QUALITY_GCP_SERVICE/);
  });
});

describe('resolveProject', () => {
  it('prefers the explicit COACH_QUALITY_GCP_PROJECT override without shelling out', () => {
    process.env.COACH_QUALITY_GCP_PROJECT = 'my-project';
    expect(resolveProject()).toBe('my-project');
    expect(execFileSync).not.toHaveBeenCalled();
  });

  it('falls back to `gcloud config get-value project`', () => {
    vi.mocked(execFileSync).mockReturnValue('sudoku-app-eo\n');
    expect(resolveProject()).toBe('sudoku-app-eo');
    expect(execFileSync).toHaveBeenCalledWith('gcloud', ['config', 'get-value', 'project'], expect.anything());
  });

  it('throws when gcloud reports no project', () => {
    vi.mocked(execFileSync).mockReturnValue('(unset)\n');
    expect(() => resolveProject()).toThrow(/COACH_QUALITY_GCP_PROJECT/);
  });
});

describe('fetchLogLinesGcp', () => {
  beforeEach(() => {
    process.env.COACH_QUALITY_API_URL = REMOTE_URL;
    process.env.COACH_QUALITY_GCP_PROJECT = 'sudoku-app-eo';
  });

  it('parses structured lines and drops non-JSON lines (request logs, blanks)', () => {
    const output = [
      '2026-08-25 09:47:34,676 INFO [c.s.c.v.VertexCoachClient] (t) {"type":"COACH_REQUEST","pid":"g1","cid":"c1"}',
      'GET 200 https://.../api/v1/players/me',
      '2026-08-25 09:47:36,485 INFO [c.s.c.v.VertexCoachClient] (t) {"type":"COACH_RESPONSE","pid":"g1","cid":"c1","fallback":false}',
      '',
    ].join('\n');
    vi.mocked(execFileSync).mockReturnValue(output);

    const lines = fetchLogLinesGcp('2026-08-25T09:47:00Z');

    expect(lines).toEqual([
      { type: 'COACH_REQUEST', pid: 'g1', cid: 'c1' },
      { type: 'COACH_RESPONSE', pid: 'g1', cid: 'c1', fallback: false },
    ]);
  });

  it('scopes the query by service, ascending order, and the since timestamp', () => {
    vi.mocked(execFileSync).mockReturnValue('');
    fetchLogLinesGcp('2026-08-25T09:47:00Z');

    const [cmd, args] = vi.mocked(execFileSync).mock.calls[0];
    expect(cmd).toBe('gcloud');
    expect(args.slice(0, 2)).toEqual(['logging', 'read']);
    const filter = args[2];
    expect(filter).toContain('resource.labels.service_name="sudoku-rcg-vertex-validate"');
    expect(filter).toContain('timestamp>="2026-08-25T09:47:00Z"');
    expect(args).toContain('asc');
    expect(args).toEqual(expect.arrayContaining(['--project', 'sudoku-app-eo']));
  });

  it('omits the timestamp clause when no since is given', () => {
    vi.mocked(execFileSync).mockReturnValue('');
    fetchLogLinesGcp();
    const filter = vi.mocked(execFileSync).mock.calls[0][1][2];
    expect(filter).not.toContain('timestamp>=');
  });
});
