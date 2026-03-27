Your goal is to update any vulnerable dependencies.

## Frontend (ui/)

1. Run `npm audit` to find vulnerable installed packages.
2. Run `npm audit fix` to apply updates.
3. Run tests and verify the updates did not break anything.

## Backend (backend/)

Leverage the OWASP dependency-check-maven plugin:

1. Run `mvn dependency-check:check`
2. Update any vulnerable dependencies.
3. Run tests and verify the updates did not break anything.

## Image Recognition (image_recognition/)

Use `pip-audit` to find and fix vulnerable Python packages:

```bash
cd image_recognition && source .venv/bin/activate
pip install pip-audit
pip-audit -r requirements.txt
pip-audit -r requirements-dev.txt
```

For each reported vulnerability:
1. Update the affected package to a safe version in `requirements.txt` or `requirements-dev.txt`.
2. Re-install: `pip install -r requirements-dev.txt`
3. Run tests to verify nothing broke: `python -m pytest -m "not real_images and not e2e"`

## GitHub Actions

Update GitHub Actions versions using the skill **update-github-actions** found in (~/.claude/skills/update-github-actions/).
