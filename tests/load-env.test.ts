import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { afterEach, describe, expect, it } from "vitest";

const helperPath = resolve("scripts/load-env.sh");
const temporaryDirectories: string[] = [];

function runLoader(contents: string, environment: NodeJS.ProcessEnv = {}) {
  const directory = mkdtempSync(join(tmpdir(), "speaive-env-test-"));
  temporaryDirectories.push(directory);
  const envFile = join(directory, ".env");
  writeFileSync(envFile, contents);

  return spawnSync(
    "/bin/sh",
    [
      "-c",
      '. "$1"\nload_env_defaults "$2"\nprintf "%s|%s" "${SPEAIVE_BACKUP_DIR-}" "${SPEAIVE_ADMIN_PASSWORD_HASH-}"',
      "sh",
      helperPath,
      envFile,
    ],
    {
      encoding: "utf8",
      env: {
        PATH: process.env.PATH,
        ...environment,
      },
    },
  );
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe("load_env_defaults", () => {
  it("keeps explicit command environment values", () => {
    const result = runLoader(
      "SPEAIVE_BACKUP_DIR=/from-dot-env\nSPEAIVE_ADMIN_PASSWORD_HASH='hash-from-dot-env'\n",
      { SPEAIVE_BACKUP_DIR: "/from-command" },
    );

    expect(result.status).toBe(0);
    expect(result.stdout).toBe("/from-command|hash-from-dot-env");
  });

  it("treats an explicitly empty value as an override", () => {
    const result = runLoader("SPEAIVE_BACKUP_DIR=/from-dot-env\n", {
      SPEAIVE_BACKUP_DIR: "",
    });

    expect(result.status).toBe(0);
    expect(result.stdout).toBe("|");
  });

  it("preserves quoted dollar signs from the env file", () => {
    const result = runLoader(
      "SPEAIVE_ADMIN_PASSWORD_HASH='$2y$12$literal-bcrypt-value'\n",
    );

    expect(result.status).toBe(0);
    expect(result.stdout).toBe("|$2y$12$literal-bcrypt-value");
  });
});
