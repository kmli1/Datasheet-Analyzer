# Security policy

## Reporting a vulnerability

Please **do not** publish exploit instructions, secret values, or private document content in a public issue.

If GitHub's **Report a vulnerability** option is enabled for this repository, use that private reporting channel. Otherwise, contact the repository maintainer privately through an available contact method on their GitHub profile and request a private disclosure path before sharing details. A dedicated security contact has not yet been published.

For ordinary non-sensitive defects, open a GitHub issue with steps to reproduce and relevant Android/Gradle version details.

## Security scope

The project includes source selection policies, PDF validation checks and an isolated PDF processing service. These are **defense-in-depth measures**, not guarantees that all malicious or malformed documents are detected or safely processed.

- Treat untrusted PDFs, redirects and HTML as potentially hostile input.
- Do not commit keys, tokens, signing materials, private logs or personal files.
- Never treat software-extracted electrical parameters as authoritative without checking the original manufacturer datasheet.

## Supported versions

The only current source version identified in the repository is `1.0.10`. Published security update commitments and supported binary releases have **not yet** been established.
