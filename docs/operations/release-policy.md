# Release Policy

This document defines how changes move from `develop` to `main`, how releases are tagged, and what evidence is required before promotion.

## Branch model

| Branch | Purpose | Rules |
|---|---|---|
| `develop` | Integration branch for approved work | PR-only. CI must be green. No direct feature work without issue/PR flow. |
| `release/vX.Y.Z` | Release preparation from `develop` | Created from `origin/develop` for a specific version. Only release-scoped changes: changelog, version bumps, release notes/evidence, small release fixes. |
| `main` | Default/released branch | Receives releases only through PR from `release/vX.Y.Z` (or an equivalent release-promotion PR). No direct promotion from `develop`. |
| `fix/*`, `docs/*`, `feat/*`, etc. | Normal work branches | Base from `develop`, PR to `develop`, linked approved issue. |

## Versioning and tags

- Use semantic versioning tags: `vMAJOR.MINOR.PATCH`.
- Create the tag on the release commit after the release PR is merged, or as part of the documented release flow, pointing at the promoted commit.
- Tag names must match changelog sections and release PR title/body.
- Do not retag published releases except with explicit maintainer authorization; prefer a new patch release.

## Standard release flow

1. **Confirm scope on `develop`.** Required PRs merged, CI green, no unresolved release blocker.
2. **Create release branch:** `release/vX.Y.Z` from `origin/develop`.
3. **Prepare release artifacts on that branch:**
   - Update `CHANGELOG.md` with `## [X.Y.Z] - YYYY-MM-DD`.
   - Include only user-visible/relevant changes and known limitations.
   - Add any release-specific docs/evidence references.
4. **Open PR to `main`.** Title: `chore(release): promote vX.Y.Z to main` (or equivalent). Body includes included PRs/issues, verification, known follow-ups, and risk notes.
5. **Merge policy for release PR:** use a merge strategy that preserves the intended release history. Do not rewrite published history.
6. **Tag and publish:** create/push `vX.Y.Z` on the promoted commit; create GitHub release notes from changelog.
7. **Back-merge/sync:** ensure `develop` contains release changelog/version metadata if it was added only on the release branch.

## Hotfix flow

- For urgent fixes to a released version, branch from `main` or the relevant release tag/branch as `fix/<description>` or `release/vX.Y.Z-hotfix` as agreed.
- Open PR to `main` with the fix and changelog entry.
- Tag a new patch version.
- Cherry-pick or merge back into `develop` so the fix is not lost.

## Evidence before promoting to main

- CI green on the release branch/PR.
- Changelog entry present and accurate.
- Release PR describes included scope, verification, and known follow-ups.
- No secrets/credentials in diff, logs, or docs.
- ADR/operational claims must match evidence status (e.g., do not claim Azure acceptance when ADR-11 gates are pending).
- If deployment is involved, include run URLs/log references and note manual workarounds or pending gates.

## What must not happen

- No direct `develop -> main` merge as routine practice.
- No feature work committed directly on `release/*` except release fixes.
- No force-push to `main` or `develop` without explicit maintainer authorization.
- No release tag without changelog/release PR traceability.

## Current repository signals

This policy formalizes existing practice observed in repository history: release tags `v1.0.0`-`v1.0.2`, release changelog commits, and release promotion PRs such as #138 and #142.
