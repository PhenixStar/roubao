# Roubao Documentation Guide

Welcome to the Roubao project documentation! This guide helps you navigate all available documentation.

---

## Quick Start

New to Roubao? Start here:

1. **[README.md](./README.md)** or **[README_EN.md](./README_EN.md)** - Project overview, features, setup instructions
2. **[docs/project-overview-pdr.md](./docs/project-overview-pdr.md)** - Product vision, requirements, success metrics
3. **[docs/codebase-summary.md](./docs/codebase-summary.md)** - File structure, module breakdown, statistics

---

## Documentation Structure

### Core Documentation (`/docs`)

#### 1. **codebase-summary.md** - File-by-File Code Reference
- Directory structure overview
- LOC breakdown by module
- Core module descriptions (Agent, Controller, Tools, Skills, VLM, Data, UI)
- Key execution flows
- Design patterns used
- Security model

**Use this when:** You need to understand how code is organized, find where specific functionality lives, or understand data flow.

#### 2. **system-architecture.md** - System Design & Architecture
- 7-layer architecture diagram
- Execution flow (with ASCII diagrams)
- 3 VLM execution modes (OpenAI, GUI-Owl, MAI-UI)
- Data flow for settings & persistence
- Skill resolution pipeline
- Screenshot processing pipeline
- Component interactions
- Concurrency model
- Error handling & resilience
- Security architecture
- Performance optimizations

**Use this when:** You need to understand how components interact, design new features, or optimize performance.

#### 3. **code-standards.md** - Coding Guidelines & Best Practices
- Kotlin naming conventions
- File organization patterns
- KDoc documentation standards
- Coroutine & threading guidelines
- Error handling strategies
- Memory & performance best practices
- Security practices (shell escaping, URL validation, API key storage)
- Testing conventions
- Compose UI patterns
- Dependency injection setup
- Code review checklist

**Use this when:** You're contributing code and want to follow project standards, or reviewing code for consistency.

#### 4. **project-overview-pdr.md** - Product Development Requirements
- Executive summary & problem statement
- Functional requirements (FR-1 through FR-7)
  - Multi-model VLM support
  - Task execution via tools
  - Skill-based delegation
  - Multi-step automation loop
  - User interface
  - Floating overlay service
  - Execution history & logging
- Non-functional requirements (Security, Performance, Reliability, Compatibility, Maintainability)
- Architecture & technical design
- Success metrics (with current v1.4.2 status)
- Development roadmap (phases 1-5)
- Known limitations & future improvements
- Risk assessment
- Acceptance criteria

**Use this when:** You need to understand product goals, requirements, or what defines "done" for a feature.

#### 5. **project-roadmap.md** - Development Timeline & Planned Features
- High-level timeline (phases 1-5)
- Current status v1.4.2 (stable, hardened)
- Phase 4: Advanced Features (v1.5)
  - Priority P1: Local models, OCR, workflow composition
  - Priority P2: IFTTT/Zapier, marketplace, i18n, gesture guidance, profiling
- Phase 5: Community & Distribution (v2.0)
  - F-Droid integration
  - Google Play Store release
  - Web dashboard
  - Third-party API
- Backlog items & research areas
- Risk mitigation
- Success metrics & KPIs
- Release schedule
- Contribution guidelines

**Use this when:** You want to know what's planned next, see the big picture, or understand where to contribute.

---

## Navigation by Role

### For New Contributors
1. Read [README.md](./README.md) (project overview)
2. Read [docs/project-overview-pdr.md](./docs/project-overview-pdr.md) (understand goals)
3. Read [docs/codebase-summary.md](./docs/codebase-summary.md) (understand structure)
4. Read [docs/code-standards.md](./docs/code-standards.md) (follow guidelines)
5. Pick an issue to start with

### For Backend Developers
1. [docs/system-architecture.md](./docs/system-architecture.md) - Component design
2. [docs/codebase-summary.md](./docs/codebase-summary.md) - Agent/Tools/VLM layers
3. [docs/code-standards.md](./docs/code-standards.md) - Best practices
4. Look at specific files in `app/src/main/java/com/roubao/autopilot/agent/`

### For UI/UX Developers
1. [docs/system-architecture.md](./docs/system-architecture.md) - UI layer overview
2. [docs/codebase-summary.md](./docs/codebase-summary.md) - UI section (7 screens)
3. [docs/code-standards.md](./docs/code-standards.md) - Compose UI patterns
4. Look at `app/src/main/java/com/roubao/autopilot/ui/screens/`

### For Security Auditors
1. [docs/code-standards.md](./docs/code-standards.md) - Security practices section
2. [docs/system-architecture.md](./docs/system-architecture.md) - Security architecture section
3. [docs/project-overview-pdr.md](./docs/project-overview-pdr.md) - Security requirements
4. Review specific files: `*Tool.kt`, `SettingsManager.kt`, `VLMClient.kt`

### For Product Managers
1. [docs/project-overview-pdr.md](./docs/project-overview-pdr.md) - Requirements & metrics
2. [docs/project-roadmap.md](./docs/project-roadmap.md) - Timeline & features
3. [README.md](./README.md) - Market positioning

### For System Architects
1. [docs/system-architecture.md](./docs/system-architecture.md) - Full architecture
2. [docs/project-overview-pdr.md](./docs/project-overview-pdr.md) - Design decisions
3. [docs/codebase-summary.md](./docs/codebase-summary.md) - Module breakdown

---

## Key Sections by Topic

### Understanding Execution Flow
- **Start here:** [docs/system-architecture.md - Execution Flow Diagram](./docs/system-architecture.md#execution-flow-diagram)
- **Details:** [docs/codebase-summary.md - Key Flows](./docs/codebase-summary.md#key-flows)

### VLM Integration
- **Overview:** [docs/system-architecture.md - VLM Client Architecture](./docs/system-architecture.md#vlm-client-architecture)
- **Code details:** [docs/codebase-summary.md - VLM Client Layer](./docs/codebase-summary.md#5-vlm-client-layer-1060-loc)

### Skill System
- **Architecture:** [docs/system-architecture.md - Skill Resolution Pipeline](./docs/system-architecture.md#skill-resolution-pipeline)
- **Code:** [docs/codebase-summary.md - Skills Layer](./docs/codebase-summary.md#3-skills-layer-1140-loc)
- **Requirements:** [docs/project-overview-pdr.md - FR-3](./docs/project-overview-pdr.md#fr-3-skill-based-task-delegation)

### Tools & Execution
- **Overview:** [docs/system-architecture.md - Component Interactions](./docs/system-architecture.md#component-interactions)
- **Code:** [docs/codebase-summary.md - Tools Layer](./docs/codebase-summary.md#4-tools-layer-1800-loc)
- **Security:** [docs/code-standards.md - Security Practices](./docs/code-standards.md#security-practices)

### Security
- **Architecture:** [docs/system-architecture.md - Security Architecture](./docs/system-architecture.md#security-architecture)
- **Practices:** [docs/code-standards.md - Security Practices](./docs/code-standards.md#security-practices)
- **Requirements:** [docs/project-overview-pdr.md - NFR-1](./docs/project-overview-pdr.md#nfr-1-security)

### Performance
- **Architecture:** [docs/system-architecture.md - Performance Optimization](./docs/system-architecture.md#performance-optimization)
- **Best practices:** [docs/code-standards.md - Memory & Performance](./docs/code-standards.md#memory--performance)
- **Requirements:** [docs/project-overview-pdr.md - NFR-2](./docs/project-overview-pdr.md#nfr-2-performance)

### Testing
- **Guidelines:** [docs/code-standards.md - Testing Conventions](./docs/code-standards.md#testing-conventions)
- **Strategy:** [docs/system-architecture.md - Testing Strategy](./docs/system-architecture.md#testing-strategy)
- **Requirements:** [docs/project-overview-pdr.md - Acceptance Criteria](./docs/project-overview-pdr.md#acceptance-criteria-v142-release)

---

## Document Statistics

| Document | Purpose | LOC | Read Time |
|----------|---------|-----|-----------|
| **codebase-summary.md** | Code reference | 450 | 15 min |
| **system-architecture.md** | Architecture & design | 550 | 20 min |
| **code-standards.md** | Coding guidelines | 650 | 25 min |
| **project-overview-pdr.md** | Product requirements | 550 | 20 min |
| **project-roadmap.md** | Development timeline | 480 | 18 min |

**Total Reading Time:** ~98 minutes for all documentation

---

## How to Use This Documentation

### Finding Information
1. **Use the navigation above** for your role
2. **Use "Key Sections by Topic"** to jump to relevant sections
3. **Use Ctrl+F** (or Cmd+F) to search within documents
4. **Check the table of contents** at the start of each document

### Keeping Documentation Updated
When you make changes to the codebase:
1. Update the relevant documentation file
2. Update statistics (LOC counts, metrics) if code size changes
3. Update version information if releasing
4. Add an entry to the roadmap if planning new features

### Contributing Documentation
- Use **Markdown format** (compatible with GitHub)
- Keep line length ≤100 characters for readability
- Use **clear headings** (H1-H4 levels)
- Include **code examples** where helpful
- Add **ASCII diagrams** for complex flows
- Keep documents **under 800 lines** (split into multiple if needed)

---

## Related Resources

### External Documentation
- **Android Development:** https://developer.android.com/
- **Jetpack Compose:** https://developer.android.com/jetpack/compose
- **Shizuku:** https://shizuku.rikka.app/
- **OkHttp:** https://square.github.io/okhttp/
- **Kotlin:** https://kotlinlang.org/docs/

### Project Links
- **GitHub Repository:** https://github.com/Turbo1123/roubao
- **MobileAgent Original:** https://github.com/X-LANCE/Mobile-Agent
- **Issue Tracker:** https://github.com/Turbo1123/roubao/issues
- **Discussions:** https://github.com/Turbo1123/roubao/discussions

---

## FAQ

**Q: Where do I find code for feature X?**
A: Use [codebase-summary.md](./docs/codebase-summary.md) to locate the module, then check that directory.

**Q: How do I add a new VLM client?**
A: Follow [code-standards.md](./docs/code-standards.md) and the pattern in `vlm/` directory.

**Q: What's the process for adding a new skill?**
A: See [docs/project-roadmap.md - Community Skill Marketplace](./docs/project-roadmap.md#p2-1-community-skill-marketplace-planned).

**Q: How should I test my changes?**
A: Follow [code-standards.md - Testing Conventions](./docs/code-standards.md#testing-conventions).

**Q: What are the current priorities?**
A: See [docs/project-roadmap.md - Phase 4 P1](./docs/project-roadmap.md#priority-p1-high-priority---core-impact).

---

## Version History

| Date | Version | Changes |
|------|---------|---------|
| Feb 13, 2025 | 1.0 | Initial documentation creation |

---

## Feedback & Improvements

Found outdated information or have suggestions? Please:
1. Open an issue on GitHub (include doc name & section)
2. Submit a PR with corrections
3. Start a discussion for larger changes

Your feedback helps keep documentation accurate and useful!

