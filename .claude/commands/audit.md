Your goal is to update any vulnerable depenednecies.

Do the following for the ui directory:

1. Run `npm audit` to find vulnerable installed packages in this project.
2. Run `npm audit fix` to apply updates
3. Run tests and verify the updates did not break anything

For the backend leverage the OWASP dependency-check-maven plugin:

1. Run `mvn dependency-check:check`
2. Update any vulnerable dependencies
3. Run tests and verify the updates did not break anything

Update GitHub actions versions using the skill **update-github-actions** found in (~/.claude/skills/update-github-actions/).
