# Pull Request Description

Include a summary of the changes, the problem or feature addressed, and the expected impact on the API contract, security, and data.

---

## Type of Change

- [ ] 🚀 New feature (`feature`)
- [ ] 🐞 Bug fix (`bug fix`)
- [ ] 🔧 Refactoring with no functional change (`refactor`)
- [ ] 📝 Documentation (`docs`)
- [ ] 🎨 Style / formatting (`style`)
- [ ] ⚙️ Infrastructure, CI/CD, dependencies, or migrations (`chore`)
- [ ] 🔒 Security or privacy (`security`)

## How Was It Tested?

Describe the validations performed and how to reproduce them locally.

- [ ] **Build and tests:** ran `.\mvnw.cmd verify` and all tests passed.
- [ ] **Testcontainers:** integration tests ran with Docker running.
- [ ] **ArchUnit:** no layer or convention violations were introduced.
- [ ] **Flyway migration:** created and applied the required migration (when applicable).
- [ ] **Swagger / Contract:** validated the changed endpoints at `http://localhost:8080/swagger-ui.html`.
- [ ] **Authorization:** verified that the resource requires authentication and the correct permissions.
- [ ] **Concurrency / idempotency:** validated the affected flows when applicable.
- [ ] Other tests performed:

## Visual Evidence / Logs

*If applicable, paste relevant logs, Swagger screenshots, or evidence of the validated flow.*

## Checklist

- [ ] My code follows [`docs/CONVENTIONS.md`](https://github.com/felipevenas/api-dev-rats/blob/main/docs/CONVENTIONS.md) and the project layers.
- [ ] I self-reviewed and removed dead or experimental code.
- [ ] I added or updated tests to cover the changed behavior.
- [ ] I updated [`CHANGELOG.md`](https://github.com/felipevenas/api-dev-rats/blob/main/CHANGELOG.md) when the change is relevant to users.
- [ ] I did not commit secrets, tokens, `.env`, or private repository data.
- [ ] I updated documentation and the API contract when necessary.
