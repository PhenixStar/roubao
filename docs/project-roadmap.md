# Roubao Development Roadmap

**Last Updated:** February 13, 2025
**Current Version:** v1.4.2 (hardened)
**Overall Project Status:** Stable, Active Maintenance

---

## High-Level Timeline

```
2024 Q4        2025 Q1              2025 Q2-Q3         2025 Q4+
├────────────┤├──────────────────┤├────────────────┤├──────────────┤
Foundation   Skills & Hardening   Advanced Features  Community
(v1.0)       (v1.1-v1.4.2)        (v1.5)             (v2.0)
```

---

## Current Status: v1.4.2 (Stable - Hardened)

### Release Date: February 2025

**Focus:** Security hardening & stability improvements

### Completed Features ✓
- [x] MobileAgent core orchestration (1,211 LOC)
- [x] 3 VLM execution modes (OpenAI, GUI-Owl, MAI-UI)
- [x] 8 tool implementations (Shell, HTTP, OpenApp, DeepLink, Clipboard, SearchApps, etc.)
- [x] Skill-based task delegation (20+ predefined skills)
- [x] 5 Compose screens (Home, Settings, History, Capabilities, Onboarding)
- [x] OverlayService (floating UI widget)
- [x] Execution history & logging
- [x] Conversation memory for multi-turn context
- [x] **Security Hardening:**
  - Shell injection mitigation (argument escaping)
  - SSRF prevention (URL validation)
  - Bitmap memory recycling (OOM prevention)
  - OkHttp response lifecycle fixes
  - HTTP 429 rate-limiting handling
  - @Volatile thread-safety annotations
  - Token budget enforcement
  - Coordinate clamping (screen bounds validation)
  - 29 total security & stability fixes

### Current Metrics
- **LOC:** ~12,450 (36 Kotlin files)
- **APK Size:** ~20-25 MB
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **Crash Rate:** < 0.5% (Firebase Crashlytics)
- **Memory Peak:** ~125 MB average
- **Build:** Gradle 8.2, Kotlin 1.9.20
- **Code Coverage:** 68% (agent + tools)

### Known Issues (v1.4.2)
1. MAI-UI local model integration incomplete (placeholder only)
2. Screenshot quality varies by device manufacturer
3. Chinese UI optimization; less optimal for non-Chinese apps
4. Complex multi-window scenarios not fully supported
5. Video/3D game automation still challenging

---

## Phase 4: Advanced Features (v1.5 - Planned Q2-Q3 2025)

### Priority: P1 (High Priority - Core Impact)

#### P1-1: MAI-UI Local Model Support [PLANNED]
**Objective:** Enable offline AI inference without cloud API dependency

**Requirements:**
- Integrate lightweight local vision models (LLaVA, Phi-Vision, or equivalent)
- Support Android GPU acceleration (NNAPI, TensorFlow Lite)
- Implement coordinate normalization (0-999 range system)
- Fallback to local model if cloud API unavailable or slow

**Implementation Tasks:**
- [ ] Research & select optimal lightweight vision model
- [ ] Integrate TensorFlow Lite or ONNX Runtime
- [ ] Build model quantization pipeline
- [ ] Implement coordinate normalization layer
- [ ] Add model download & caching mechanism
- [ ] Update SettingsScreen with local model configuration
- [ ] Performance testing (latency, memory, battery)
- [ ] Fallback logic in MobileAgent

**Success Criteria:**
- Inference latency < 15 seconds (vs 30s+ for cloud APIs)
- Memory increase < 50 MB (total < 200 MB)
- Accuracy ≥80% for basic UI automation
- Works on mid-range devices (2GB+ RAM)
- Model size < 500 MB on device

**Estimated Effort:** 4-6 weeks (research + integration)

#### P1-2: Improved Screenshot Quality & OCR [PLANNED]
**Objective:** Better visual understanding through preprocessing & text recognition

**Requirements:**
- Implement smart screenshot preprocessing (edge detection, contrast enhancement)
- Add OCR capability (identify text elements on screen)
- Normalize screenshots across different device sizes/densities
- Generate rich metadata (detected text, UI elements, layout)

**Implementation Tasks:**
- [ ] Integrate ML Kit for text detection (on-device OCR)
- [ ] Implement image enhancement pipeline
- [ ] Add element detection (buttons, input fields, images)
- [ ] Create screenshot metadata structure
- [ ] Update Manager to use enriched screenshot data
- [ ] Optimize for latency (< 5 seconds preprocessing)

**Success Criteria:**
- OCR accuracy > 90% for readable text
- Preprocessing latency < 2 seconds
- Element detection identifies ≥95% of interactive UI
- Memory overhead < 20 MB

**Estimated Effort:** 3-4 weeks

#### P1-3: Workflow Composition & Task Chaining [PLANNED]
**Objective:** Allow users to compose multi-step workflows and save them

**Requirements:**
- UI for building workflows (drag-drop or form-based)
- Support for conditional logic (if-then branches)
- Task templating (reusable steps with parameters)
- Workflow persistence & versioning
- Scheduled execution (at specific times or intervals)

**Implementation Tasks:**
- [ ] Design workflow data model (tasks, conditions, parameters)
- [ ] Create WorkflowBuilder UI (Compose screens)
- [ ] Implement workflow execution engine
- [ ] Add persistence layer (Room database)
- [ ] Integrate scheduling (AlarmManager or WorkManager)
- [ ] Build workflow editor & management UI
- [ ] Testing & validation

**Success Criteria:**
- Support ≥5 conditional operators (and, or, equals, contains, etc.)
- Execute workflows with ≥10 chained steps
- Persist ≥100 workflows locally
- Scheduled execution success rate ≥95%

**Estimated Effort:** 6-8 weeks

---

### Priority: P2 (Medium Priority - Nice to Have)

#### P2-1: Integration with IFTTT / Zapier [PLANNED]
**Objective:** Connect Roubao with popular automation platforms

**Requirements:**
- IFTTT applet integration (trigger Roubao tasks from IFTTT)
- Zapier webhook support (trigger tasks via webhooks)
- Two-way data flow (send results back to IFTTT/Zapier)

**Implementation Tasks:**
- [ ] Design webhook API for task triggering
- [ ] Implement webhook receiver (HttpTool extension)
- [ ] Build IFTTT applet schema
- [ ] Add Zapier action configuration
- [ ] Document API for third-party integrations

**Success Criteria:**
- ≥3 IFTTT applets available
- Zapier integration tested & working
- Webhook latency < 5 seconds
- Rate limiting enforced (100 requests/min)

**Estimated Effort:** 3-4 weeks

#### P2-2: Community Skill Marketplace [PLANNED]
**Objective:** Allow users to share and discover custom skills

**Requirements:**
- Central repository for community-created skills
- User submission & review workflow
- Skill rating & comments system
- One-click install from marketplace

**Implementation Tasks:**
- [ ] Design skill submission & review process
- [ ] Build marketplace backend (simple REST API or Firebase)
- [ ] Create marketplace UI in app
- [ ] Implement skill installation mechanism
- [ ] Add security validation for submitted skills
- [ ] Create skill templates & documentation

**Success Criteria:**
- ≥50 community skills in marketplace within 3 months
- User rating system (1-5 stars)
- One-click install with 1-click uninstall
- Review process < 24 hours

**Estimated Effort:** 4-6 weeks

#### P2-3: Multi-Language UI (i18n) [PLANNED]
**Objective:** Support major languages (Chinese, English, Spanish, French, Japanese)

**Implementation Tasks:**
- [ ] Extract all user-facing strings to `strings.xml`
- [ ] Create translations for priority languages
- [ ] Implement language selection in SettingsScreen
- [ ] Test UI layout with long strings
- [ ] Add RTL language support if needed

**Success Criteria:**
- ≥5 languages supported
- 100% of UI strings translated
- Layout responsive to different string lengths
- Language preference persists across sessions

**Estimated Effort:** 2-3 weeks

#### P2-4: Real-Time Gesture Guidance [PLANNED]
**Objective:** Visual hints showing where the AI intends to tap/swipe

**Requirements:**
- Highlight target coordinates on screen before execution
- Show swipe path visualization
- Brief delay (500ms) to let user review before action executes
- Option to cancel action if guidance incorrect

**Implementation Tasks:**
- [ ] Extend OverlayService to show guidance visuals
- [ ] Parse coordinate predictions from action strings
- [ ] Draw circles/lines on overlay
- [ ] Add confirmation UI (Cancel / Continue buttons)
- [ ] Implement gesture prediction visualization

**Success Criteria:**
- Guidance displayed within 1 second of action decision
- User can cancel 95% of actions in time
- Visual clarity on low-density screens

**Estimated Effort:** 2-3 weeks

#### P2-5: Performance Profiling & Optimization [PLANNED]
**Objective:** Identify bottlenecks and optimize execution speed

**Implementation Tasks:**
- [ ] Add execution timeline logging
- [ ] Measure component latencies (screenshot, VLM, execution)
- [ ] Optimize screenshot compression algorithm
- [ ] Cache VLM responses for similar screenshots
- [ ] Profile memory allocation hotspots
- [ ] Reduce unnecessary allocations

**Success Criteria:**
- Average execution time < 45 seconds
- Memory leaks eliminated (Leaky Canary testing)
- Battery impact < 10% per 1-hour usage
- Screenshot latency < 3 seconds

**Estimated Effort:** 2-3 weeks

---

## Phase 5: Community & Distribution (v2.0 - Planned Q4 2025+)

### Priority: P1 (High Priority)

#### P1-1: F-Droid Integration [PLANNED]
**Objective:** Distribute via F-Droid for privacy-conscious users

**Requirements:**
- Remove Firebase Crashlytics (proprietary)
- Add F-Droid metadata (descriptions, screenshots, privacy policy)
- Ensure reproducible builds
- Support auto-updates via F-Droid

**Implementation Tasks:**
- [ ] Make Crashlytics optional (compile-time flag)
- [ ] Create F-Droid metadata files
- [ ] Ensure builds are reproducible
- [ ] Submit to F-Droid registry
- [ ] Maintain F-Droid release cycle

**Success Criteria:**
- App available on F-Droid main repository
- 0 critical security issues in F-Droid scan
- Builds are reproducible & verifiable

**Estimated Effort:** 2-3 weeks

#### P1-2: Google Play Store Release [PLANNED]
**Objective:** Reach mainstream Android users

**Requirements:**
- Play Store compliance (content rating, privacy policy, permissions justification)
- App signing & release management
- Staged rollout (25% → 50% → 100%)
- Monitoring & quick rollback capability

**Implementation Tasks:**
- [ ] Complete Play Store questionnaire
- [ ] Create privacy policy & terms of service
- [ ] Build release management pipeline
- [ ] Implement in-app crash reporting (non-Firebase option)
- [ ] Create app store graphics & screenshots
- [ ] Set up versioning & release notes workflow

**Success Criteria:**
- Available on Google Play Store
- ≥4.5 star rating from ≥100 reviews
- 0 critical policy violations
- < 1% crash rate

**Estimated Effort:** 3-4 weeks

#### P1-3: Web Dashboard for Remote Control [PLANNED]
**Objective:** Control phone from desktop browser

**Requirements:**
- Web UI for task submission
- Real-time status updates (WebSocket)
- Screenshot preview from web
- Task history & logs accessible from web

**Implementation Tasks:**
- [ ] Design REST API for task management
- [ ] Build web UI (React or Vue)
- [ ] Implement WebSocket for real-time updates
- [ ] Add authentication (simple token or OAuth)
- [ ] Deploy to cloud (Firebase Hosting, Vercel, or self-hosted)
- [ ] Mobile-responsive design

**Success Criteria:**
- Web UI responsive on desktop & tablet
- Task submission latency < 2 seconds
- Real-time updates within 1 second
- Support ≥10 concurrent users

**Estimated Effort:** 4-6 weeks

---

### Priority: P2 (Medium Priority)

#### P2-1: API for Third-Party Integrations [PLANNED]
**Objective:** Allow external apps to trigger Roubao tasks via intent or API

**Requirements:**
- Public API documentation
- Intent-based trigger mechanism (for Android apps)
- Webhook support (for web services)
- Rate limiting & authentication

**Implementation Tasks:**
- [ ] Design intent schema
- [ ] Document API (OpenAPI/Swagger)
- [ ] Implement intent receiver in MainActivity
- [ ] Build example integrations (3+)
- [ ] Create SDK for easier integration

**Success Criteria:**
- ≥5 third-party apps integrate with Roubao
- API documentation clear & complete
- Example code provided in multiple languages

**Estimated Effort:** 3-4 weeks

#### P2-2: Analytics & Usage Insights [PLANNED]
**Objective:** Track usage patterns (opt-in) to guide future development

**Requirements:**
- Privacy-first analytics (no personally identifiable information)
- Task type distribution
- Success/failure rates by task
- Popular skills & tools
- Device & Android version statistics

**Implementation Tasks:**
- [ ] Design privacy-compliant analytics schema
- [ ] Implement analytics event logging
- [ ] Create dashboard for viewing statistics
- [ ] Add opt-in/opt-out UI in SettingsScreen
- [ ] Aggregate data securely

**Success Criteria:**
- 0 PII data collected
- ≥50% opt-in rate
- Monthly reports generated automatically
- Data retention < 12 months

**Estimated Effort:** 2-3 weeks

---

## Backlog Items (Future Consideration)

### Nice-to-Have Features
- [ ] Video/GIF recording of task execution (for debugging & sharing)
- [ ] AI-powered task suggestion (based on user history)
- [ ] Voice control interface ("Tell Roubao to...")
- [ ] Accessibility mode improvements (TalkBack, Switch Control)
- [ ] Custom prompt templates (advanced users)
- [ ] A/B testing framework for VLM prompt optimization
- [ ] Auto-retry with different VLM if first fails
- [ ] Screenshot compression algorithm (adaptive quality)
- [ ] Dark mode theme refinements
- [ ] Biometric authentication for sensitive settings

### Research Areas
- [ ] Multimodal understanding (text + layout + images)
- [ ] End-to-end learning from execution failures
- [ ] Attention mechanisms for UI element focus
- [ ] Cross-app workflow composition (system-level automation)
- [ ] Federated learning (improve models without centralized data)

---

## Risk Mitigation & Contingency Plans

| Risk | Planned Mitigation |
|------|-------------------|
| **Local model accuracy too low** | Maintain cloud API fallback; continuous improvement |
| **Shizuku API breaking changes** | Contribute to Shizuku; monitor upstream; maintain compatibility layer |
| **Community adoption slow** | Marketing via demo videos, blog posts; partnerships with tech YouTubers |
| **Security vulnerabilities found** | Regular audits; quick patch releases; security bounty program |
| **Maintenance burden increases** | Recruit core team; document contribution guidelines; automate testing |

---

## Key Dependencies & Blockers

| Feature | Blocked By | Status |
|---------|-----------|--------|
| **MAI-UI Local Models** | Research on lightweight models | In Progress |
| **Workflow Scheduling** | WorkManager stability | Ready |
| **Play Store Release** | Privacy policy & compliance review | In Progress |
| **Community Marketplace** | Backend infrastructure | Planned Q3 |
| **Web Dashboard** | REST API specification | Planned Q3 |

---

## Success Metrics & KPIs

### v1.5 Release Goals (Q2-Q3 2025)
- [ ] **Code Coverage:** ≥75% (from 68%)
- [ ] **Supported Skills:** ≥30 (from 20)
- [ ] **VLM Execution Success:** ≥85% (from 78%)
- [ ] **Crash Rate:** < 0.3% (from 0.5%)
- [ ] **Average Execution Time:** < 40 seconds (from 45s)
- [ ] **GitHub Stars:** ≥500 (community engagement)

### v2.0 Release Goals (Q4 2025+)
- [ ] **Available on:** F-Droid, Google Play Store, GitHub
- [ ] **Supported Languages:** ≥5
- [ ] **Community Skills:** ≥50
- [ ] **Monthly Active Users:** ≥10,000 (estimate)
- [ ] **Code Coverage:** ≥80%
- [ ] **Documentation:** Complete + video tutorials

---

## Release Schedule

| Version | Target Date | Focus | Status |
|---------|------------|-------|--------|
| **v1.4.2** | Feb 2025 | Security hardening | ✓ Released |
| **v1.5.0** | Jun 2025 | Advanced features (local models, workflows) | Planned |
| **v1.5.1** | Jul 2025 | Bug fixes & optimization | Planned |
| **v1.6.0** | Sep 2025 | Community marketplace, i18n | Planned |
| **v2.0.0** | Dec 2025 | F-Droid/Play Store, web dashboard | Planned |

---

## Contribution Guidelines

### How to Contribute
1. **Report Issues** - GitHub Issues for bugs & feature requests
2. **Submit Skills** - Create new skill definitions in skills.json (or marketplace)
3. **Code Contributions** - Fork → branch → PR with test coverage
4. **Documentation** - Improve README, add tutorials, translate docs
5. **Translations** - Help translate UI to new languages

### Code Review Process
- [ ] Unit tests pass (≥70% coverage for changes)
- [ ] No security issues (input validation, secrets, permissions)
- [ ] Follows code standards (naming, formatting, documentation)
- [ ] Commit messages are descriptive
- [ ] Changelog updated

### Community Communication
- **Issues & PRs:** Respond within 48 hours
- **Discussions:** GitHub Discussions for architecture & design
- **Roadmap:** Quarterly planning & progress updates

---

## Conclusion

Roubao is at a **stable, production-ready state** with v1.4.2. The roadmap balances:
- **Stability:** Regular security updates & maintenance
- **Innovation:** Advanced features (local models, workflows)
- **Accessibility:** Multi-language support, marketplace, web dashboard
- **Community:** Open contribution pathways, transparent roadmap

**Next milestone:** v1.5 (Q2-Q3 2025) with local model support and workflow composition.

For questions or suggestions, please open an issue on [GitHub](https://github.com/Turbo1123/roubao).

