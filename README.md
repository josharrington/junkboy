# 🛡️ Junkboy SMS Filter

**An intelligent, on-device SMS filtering application for Android with a user-editable pipeline: classify each message with machine learning, then decide with ordered rules you can inspect, test and export.**

*Inspired by [Junkman](https://apps.apple.com/tr/app/junkman-a-i-sms-spam-blocker/id1591815272) for iOS, bringing similar AI-powered SMS filtering capabilities to Android with enhanced features and open-source transparency.*

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Material Design 3](https://img.shields.io/badge/UI-Material%20Design%203-purple.svg)](https://m3.material.io)
[![TensorFlow Lite](https://img.shields.io/badge/AI-TensorFlow%20Lite-orange.svg)](https://tensorflow.org/lite)

---

## 🌟 Features

### 🧭 **Editable Message Pipeline**
Filtering is an ordered list of steps you control, not a hidden hard-coded path.
- **Steps run top to bottom**: each step has checks and actions; a matching step runs its actions.
- **`Block` is the only terminal action**: reaching the bottom means allow, so an allow rule can sit above the blocker.
- **Labels carry forward**: a step can relabel a message, and later steps can match on that category.
- **Checks**: message/sender text or regex, category, contact, OTP, outbound history, opt-out directive, classifier verdict, allowed sender, allowed content.
- **Actions**: pass, label, block, reply `STOP`, mute, delete.
- **YAML in and out**: export the whole pipeline, import it on another phone, or paste a single step template.
- **Suggested rules**: six ready-made templates (political STOP, parcel-fee scam, crypto doubling, lottery prize, bank phishing, unsubscribe marketing) with their YAML and impact on 20 fixture messages. Nothing is added without approval.

### 🤖 **Intelligent SMS Classification**
- **On-device model**: TensorFlow Lite classifier categorizes each message; the model is evidence, the pipeline is policy.
- **Rule tier**: keyword and regex filtering remains available as a deterministic fallback.
- **Real-time processing**: incoming SMS is evaluated in a foreground service, with the whole message body reconstructed for multipart messages.
- **Classifier verdict as a check**: `spam verdict = true` lets a blocking step prefer the model without hard-wiring it into the app.

### 🧪 **Sample Lab and Test Review**
- **20-message lab**: run a reference pipeline or your own against real-world samples, with per-message step-by-step traces.
- **Model comparison**: switch the lab between the rule tier and the bundled on-device model; model failures are labelled as rule fallback.
- **Test decisions queue**: every testing-mode decision is grouped by the step that produced it, filterable by “would block” and “would reply”, with a **Why?** trace per record.
- **Why was this filtered?** Available on any message or conversation, showing the verdict and every check that passed or failed.

### 🎚️ **Enforcing and Testing Modes**
- **Testing**: messages are delivered and notified normally; Junkboy only records what the pipeline would have done. No block, reply, mute, delete, or OTP copy.
- **Enforcing**: the pipeline's actions are performed.
- **Mode switch in the Pipeline screen**, persisted across restarts and defaulting to enforcing.

### 📱 **Modern User Experience**
- **Material Design 3 UI**: Jetpack Compose interface with dashboard, stats, and settings.
- **Message Categories**: General, Promotion, Notification, Transaction, Junk and Allowed.
- **Conversation View**: Messages grouped by sender in both Hub and Filtered screens.
- **Decision traces**: see exactly which step and check produced an outcome. Messages stored before traces were introduced say that no trace is available.

### 📥 **Unified Hub (Optional)**
- **Unified Inbox**: View SMS messages and chat notifications in one place.
- **Configurable Display**: Show SMS only, chats only, or both.
- **Conversation Grouping**: Tap to open a thread in the default SMS app.
- **Disabled by Default**: Enable in Settings → Hub Settings.

### 🔔 **Granular Notification Control**
- **Category-Specific Notifications**: Choose which message types notify.
- **Smart Notification Channels**: Separate channels per category on Android 8.0+.
- **Blocked Message Alerts**: Optional notifications for filtered messages.
- **Notification Actions**: Allow senders or open the message.
- **SMS App Notification Control**: Dismiss or mute notifications from the default SMS app.

### 🛠️ **Advanced Customization**
- **Custom Keywords and Regex**: Add spam indicators and advanced patterns.
- **Allowed Senders and Content Rules**: Let trusted senders or phrases bypass the pipeline.
- **Under Attack Mode**: Stricter junk detection during spam waves.
- **Auto-Delete Junk**: Remove blocked messages from the system SMS database when Junkboy is the default SMS app.
- **STOP replies**: The pipeline sends `STOP` only to the rules you select, with contact, OTP, allow-list and per-sender cooldown guards.

### 📊 **Comprehensive Analytics**
- **Per-rule activity**: enforced and test hits, last hit, and how many messages were allowed after a rule fired.
- **Stale-rule auto-pause**: optional; disables rules that used to fire but have not hit in 90 days.
- **Daily and total statistics** plus category breakdowns.
- **CSV export** of filtered messages for analysis.

### 🗃️ **Data Management**
- **Local Room database**: offline storage in the app's private directory.
- **Message archive**: complete history of filtered messages.
- **Bulk import of existing SMS**: evaluates the real pipeline, records test decisions, and skips duplicates.
- **Message retention**: 30/60/90/180/365 days or never, default **2 months**; applies only to Junkboy's own copies.
- **Snapshots and durable export**: daily local pipeline YAML plus a settings snapshot, and a system file-picker export you can keep anywhere.
- **Clear messages and stats**: removes messages and counters while keeping your pipeline, rules and settings.

### 📸 **Screenshots**
Feature captures live in [`docs/demo/FEATURES.md`](docs/demo/FEATURES.md), including the pipeline builder, sample lab, suggested rules, enforcement mode, decision traces, analytics, backups and retention.

---

## 🏗️ Technical Architecture

### **Core Technologies**
- **Language**: Kotlin with Coroutines for async processing
- **UI Framework**: Jetpack Compose with Material Design 3
- **Database**: Room persistence library with SQLite backend (schema v8, explicit migrations)
- **ML Engine**: TensorFlow Lite for on-device AI inference
- **Scheduling**: WorkManager for daily retention and local snapshots
- **Interchange**: SnakeYAML for pipeline import/export, Gson for internal and trace storage

### **Pipeline Architecture**
- **Pure evaluator**: `PipelineRunner.evaluate(pipeline, context)` takes a message context and returns a category, actions, blocking/reply attribution and a per-step trace. No I/O, so it is unit-tested directly.
- **Evidence vs. policy**: classification, contact, OTP, outbound history and allow-list facts are gathered once; the user's ordered steps decide what happens.
- **Execution plan**: `ExecutionPlan.forMode(evaluation, enforcing)` maps a decision to the actions allowed in the current mode; testing clears everything except notification.
- **Persistence**: the pipeline is stored as JSON in preferences, with YAML as the interchange format. Decision traces are stored per message.

### **AI Classification System**
- **TensorFlow Lite Model**: Custom-trained SMS classification model
- **Vocabulary Processing**: Tokenization and text preprocessing pipeline
- **Fallback Classification**: Rule-based backup for model-unavailable scenarios
- **Confidence Scoring**: Probabilistic classification with confidence metrics
- **Thread safety**: initialize, classify and cleanup are serialized because TFLite interpreters are not thread-safe

### **SMS Integration**
- **Broadcast Receivers**: Real-time SMS interception and processing
- **Foreground Service**: Reliable background processing with system notifications
- **Default SMS App Support**: Full SMS app capabilities for enhanced features
- **Permission Management**: Proper handling of SMS, contacts, notifications and notification-listener access
- **Notification Listener**: Intercept and manage notifications from other apps

---

## 🚀 Installation & Setup

### **Prerequisites**
- Android 7.0 (API level 24) or higher
- ~50 MB storage space for the app and ML model
- SMS permissions; contacts permission if you want STOP replies
- Notification listener permission for Hub and SMS app notification control

### **Installation Steps**

1. **Download and Install**
   ```bash
   # Clone the repository
   git clone https://github.com/ovehbe/junkboy.git
   cd junkboy
   
   # Build the APK
   ./gradlew assembleDebug
   
   # Install on connected device
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Grant Permissions**
   - **SMS Permissions**: Read, receive and send SMS messages
   - **Notification Permission**: Categorized notifications (Android 13+)
   - **Contacts Permission**: Required for "is contact" checks and STOP replies; without it Junkboy refuses to reply
   - **Notification Listener**: For Hub and SMS app notification control features

3. **Initial Configuration**
   - Open the app and complete the setup wizard
   - Review the default pipeline steps before enforcing them
   - Run the sample lab and choose Testing mode for the first few days
   - Add custom keywords or allowed senders if desired

### **Optional: Default SMS App Setup**
For enhanced features like auto-delete junk messages:
1. Go to Android Settings → Apps → Default Apps → SMS app
2. Select "Junkboy SMS Filter"
3. Enable auto-delete in Junkboy settings

---

## 📖 Usage Guide

### **Getting Started**

The app has a dynamic navigation bar that shows different items based on your configuration:

| Navigation Item | When Shown | Description |
|-----------------|------------|-------------|
| **Hub** | When enabled in settings | Unified inbox for all messages |
| **SMS** | When Junkboy is default SMS app | Full SMS client with send/receive |
| **Filtered** | Always | Browse filtered messages by category |
| **Menu** | Always | Access pipeline, settings, stats and tools |

### **How Filtering Works**

Open **Menu → Pipeline**. The screen lists steps in the order they run, with the current mode at the top.

1. Every message collects context once: category and classifier verdict, contact status, outbound history, OTP detection, opt-out directive and allow-list matches.
2. Each enabled step checks its conditions. With **All checks**, every check must pass; with **Any check**, one is enough.
3. A matching step runs its actions. `Block` ends the pipeline and suppresses the message; the bottom of the list means allow.
4. A step can label a message, and later steps can match that label. For example, an allow step can label a message `ALLOWED` before a broad blocker sees it.

Use **Export YAML** for a portable copy and **Import YAML** to restore or share a pipeline. Templates in the suggested rules screen can be added individually after reviewing their YAML and impact.

### **Testing Before Enforcing**

The mode switch on the Pipeline screen has two positions:

- **Testing** records what each message would trigger, but performs nothing: no block, no STOP reply, no mute, no delete, no OTP copy. Messages still arrive and notify normally.
- **Enforcing** performs the pipeline's actions.

While testing, open **Review test decisions** to see records grouped by the step that produced them. Filter to “would block” or “would reply”, and tap **Why?** for the full trace. When the decisions look right, switch to Enforcing.

### **Sample Lab and Suggested Rules**

- **Run 20 sample messages** evaluates a reference pipeline or your own against 20 fixtures. Tap a result for the verdict, context facts, expected outcome and every check.
- **On-device model** in the lab swaps the rule tier for the bundled classifier, so you can see where the model and your rules disagree. If the model cannot load, the screen says the results are rule fallback.
- **Suggested rules** offers ready-made steps with their YAML and how many fixture messages each would act on. Only **Add step** inserts one.

### **Hub Settings**

Configure the Hub in Settings → Hub Settings:

- **Enable Hub**: Toggle to show/hide Hub in navigation (disabled by default)
- **Display Mode**: 
  - All (SMS + Chats) - Everything in one view
  - SMS Only - Just filtered SMS messages
  - Chats Only - Just chat app notifications
- **Default View**:
  - Conversations - Messages grouped by sender
  - Individual Messages - Flat list of all messages

### **SMS App Notification Control**

Similar to Buzzkill, Junkboy can manage notifications from your default SMS app:

1. Go to Settings → SMS App Notification Control
2. Enable the master toggle
3. Configure options:
   - **Dismiss SMS Notifications**: Auto-dismiss notifications from default SMS app
   - **Blocked Only**: Only dismiss notifications for messages Junkboy blocked

This prevents duplicate notifications when both apps notify for the same message.

### **Customizing Filters**

Pipeline steps are the primary place to customize behavior. The older global filters still feed the pipeline as evidence and can be used in checks.

#### **Pipeline Steps**
- Add a step from the Pipeline screen, or start from a suggested rule
- Choose **All** or **Any** matching
- Add checks such as text matches, category, contact, OTP, opt-out, spam verdict, allow-list
- Add actions such as label, block, reply STOP, mute or delete
- Reorder with the arrows; order matters
- Disable a step with its switch instead of deleting it

#### **AI Classification**
- Enabled by default for optimal accuracy
- The classifier's verdict is available to a step as **spam verdict**
- Messages are still classified when an allow-list step matches, so the trace remains honest
- A step can read the category instead with **category is**

#### **Keyword Filtering**
```
Settings → Custom Keywords → Add
Examples:
- "URGENT OFFER" (promotional spam)
- "Bank Alert" (transaction indicator)
- "Verify your" (notification pattern)
```

#### **Regex Patterns**
```
Settings → Custom Regex Patterns → Add
Examples:
- \b\d{4}\s?\d{4}\s?\d{4}\s?\d{4}\b (credit card numbers)
- (?i)click\s+here (case-insensitive "click here")
- \$\d+\.?\d*\s?(off|discount) (discount offers)
```

#### **Notification Preferences**
1. **Global Settings**: Enable/disable all filtered or blocked notifications
2. **Category-Specific**: Choose which categories to receive notifications for
   - ✅ General Messages (personal/important)
   - ✅ Notifications (codes, alerts)
   - ✅ Transactions (banking, payments)
   - ❌ Promotions (marketing, sales)

### **Managing Allowed Senders**
- Add trusted contacts to bypass the pipeline
- Supports phone numbers and sender names
- Content allow rules can also let specific phrases through
- Allowed senders and content are still visible in traces, so a later step cannot silently override them

### **Rule Analytics and Auto-Pause**

Each step card shows enforced hits, test hits, the last hit, and how many messages were allowed after the step fired. The last figure is a rough false-positive signal, not a label.

Enable **Auto-pause stale rules** to disable rules that had enforced hits but none in 90 days. Activity lives in its own table, so message retention does not erase the evidence. A rule that has never fired is never auto-paused.

### **Retention, Snapshots and Export**

- **Settings → Message Retention**: keep messages 30, 60, 90, 180 or 365 days, or never. The default is 2 months. Changing it runs a cleanup immediately.
- Retention deletes only Junkboy's copies of filtered messages, Hub chat rows and auto-reply log rows. Google Messages, your carrier and other SMS apps are untouched.
- **Settings → Snapshot & export**: a daily local snapshot writes `pipeline-YYYYMMDD-HHMMSS.yaml` and a settings snapshot. These live in app-specific storage and are removed on uninstall.
- **Export pipeline file** uses the system file picker for a durable copy (Downloads, a synced folder, etc.). Nothing is uploaded.

### **Importing Existing Messages**

**Settings → Process Existing Messages** evaluates the device inbox through the current pipeline. Imported messages are recorded as test decisions and nothing is blocked or replied to. Duplicates are skipped by sender, body and a two-minute timestamp window, so running it again is safe.

### **Clearing Data**

**Clear messages and stats** removes stored messages, test decisions, statistics and per-rule hit counts. It keeps the pipeline, allow lists, keywords, mode, retention and backup settings.

---

## 🔍 Key Features Deep Dive

### **Pipeline Decision Model**

Junkboy separates evidence from policy:

1. **Evidence**: the TensorFlow Lite model, keyword/regex rules, allowed senders, allowed content, contact lookup, outbound history, OTP detection and opt-out detection.
2. **Policy**: the ordered steps you configure. A step can match on any of that evidence, label the message, or act.
3. **Execution**: the mode decides whether the resulting actions are performed or only recorded.
4. **Attribution**: the blocking step and the reply step are stored separately, so analytics credit both when a message is replied to and later blocked.
5. **Trace**: every step, check and action is stored per message for the “Why was this filtered?” dialog.

### **Mode Safety Guarantees**

- Testing mode performs no side effects, including OTP clipboard writes.
- STOP replies are refused to contacts, OTP messages and allow-listed senders regardless of the pipeline.
- A per-sender cooldown limits repeated replies; the send itself is logged.
- Missing contacts permission makes contact checks fail safe and disables replies; the Pipeline and test queue screens say so.

### **Conversation-Based Interface**
Both Hub and Filtered screens support conversation view:
- Messages grouped by sender for easy navigation
- Tap any conversation to open in your default SMS app
- Quick actions for allowing senders or opening apps
- “Why was this filtered?” on a conversation shows the latest message's trace and says so, because the blocked badge covers the whole thread
- Toggle between conversation and message views

### **Notification System Architecture**
- **Separate Channels**: Each category has its own Android notification channel
- **Priority Mapping**: 
  - Transactions: HIGH (banking alerts)
  - General/Notifications: DEFAULT (personal messages)
  - Promotions: LOW (marketing)
  - Blocked: MIN (spam notifications)
- **Smart Actions**: Context-appropriate quick actions per category

### **Under Attack Mode**
Enhanced protection during spam waves:
- Stricter filtering thresholds
- Expanded junk keyword detection
- Automatic temporary rule activation
- Ideal for dealing with spam campaigns

---

## 📊 Message Categories

| Category | Description | Examples | Default Notifications |
|----------|-------------|----------|---------------------|
| **General** | Personal and uncategorized messages | Friends, family, personal | ✅ Enabled |
| **Promotion** | Marketing and sales messages | Offers, discounts, ads | ❌ Disabled |
| **Notification** | System alerts and codes | OTP, delivery updates, alerts | ✅ Enabled |
| **Transaction** | Banking and payment messages | Bank alerts, payment confirmations | ✅ Enabled |
| **Junk** | Spam and unwanted messages | Scams, unwanted marketing | ❌ Disabled |
| **Allowed** | Messages from whitelisted senders | Trusted contacts, important services | ✅ Enabled |

---

## 🛠️ Development

### **Project Structure**
```
app/src/main/java/com/ovehbe/junkboy/
├── classifier/          # On-device SMS classification engine
│   └── SmsClassifier.kt
├── database/            # Room entities, DAOs and migrations (schema v8)
│   ├── AppDatabase.kt
│   ├── FilteredMessage.kt
│   ├── FilteredMessageDao.kt
│   ├── StepActivity.kt
│   └── ...
├── filters/             # Keyword, regex, allow-list and opt-out logic
│   ├── CustomFilter.kt
│   └── ...
├── pipeline/            # Pure pipeline model, evaluator and YAML
│   ├── Pipeline.kt          # Steps, checks, actions, MessageContext, Evaluation, PipelineRunner
│   ├── ExecutionPlan.kt     # Enforcing vs testing action mapping
│   ├── PipelineYaml.kt      # Import/export and strict validation
│   ├── DecisionTrace.kt     # Stored per-message trace codec
│   ├── SuggestedRules.kt
│   └── SampleMessages.kt
├── service/             # Background SMS processing
│   ├── SmsFilterService.kt
│   ├── SmsSendService.kt
│   └── NotificationListenerService.kt
├── smsreceiver/         # SMS broadcast receivers
│   └── SmsReceiver.kt
├── ui/                  # Jetpack Compose UI
│   ├── compose/
│   │   ├── JunkboyApp.kt
│   │   └── screens/
│   │       ├── PipelineScreen.kt      # Steps, mode, analytics
│   │       ├── PipelineLabScreen.kt   # 20-message lab
│   │       ├── SuggestedRulesScreen.kt
│   │       ├── TestDecisionsScreen.kt # Testing-mode review queue
│   │       ├── DecisionTraceDialog.kt
│   │       ├── MessagesScreen.kt
│   │       ├── SettingsScreen.kt
│   │       └── ...
│   └── theme/
├── utils/               # Helpers and managers
│   ├── ExistingMessagesProcessor.kt
│   ├── NotificationHelper.kt
│   ├── PreferencesManager.kt
│   └── ...
└── work/                # WorkManager jobs
    ├── RetentionWorker.kt
    ├── BackupWorker.kt
    ├── PipelineBackup.kt
    └── WorkScheduler.kt
```

### **Building from Source**
```bash
# Prerequisites
# - Android Studio (or a command-line Android SDK)
# - JDK 17
# - Android SDK 34
# - Kotlin 1.9

# Clone and build
git clone https://github.com/ovehbe/junkboy.git
cd junkboy
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Generate release build
./gradlew assembleRelease
```

### **ML Model Information**
- **Model File**: `app/src/main/assets/sms_model.tflite`
- **Labels**: `app/src/main/assets/labels.txt`
- **Vocabulary**: `app/src/main/assets/vocabulary.txt`
- **Model Info**: `app/src/main/assets/model_info.txt`

---

## 🔒 Privacy & Security

### **Data Handling**
- **Local Processing**: All SMS analysis happens on-device
- **No Cloud Sync**: Messages never leave your device
- **App-Private Storage**: The Room database is stored in the app's private directory. It is not separately encrypted; it relies on Android's per-app sandbox and device encryption. Backups and exports are written only where you choose.
- **Minimal Permissions**: Only requests necessary permissions for functionality

### **Open Source**
- **Transparent Code**: Full source code available for security auditing
- **No Tracking**: No analytics, telemetry, or user tracking
- **Community Driven**: Open to contributions and security reviews

---

## 🤝 Contributing

We welcome contributions! Here's how you can help:

### **Types of Contributions**
- 🐛 **Bug Reports**: Report issues via GitHub Issues
- 💡 **Feature Requests**: Suggest new functionality
- 🔧 **Code Contributions**: Submit pull requests
- 📖 **Documentation**: Improve README, code comments
- 🌐 **Translations**: Add support for new languages

### **Development Guidelines**
1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### **Code Style**
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Include unit tests for new features

---

## 📝 Changelog

### **v1.4.8**
- 🐞 **Review fixes** across import, data clearing and runtime safety
- 📥 **Existing-message import** now evaluates the real pipeline, records test decisions and skips duplicates instead of duplicating rows
- 🧹 **Clear messages and stats** keeps the pipeline, allow lists, keywords and settings
- 📱 **Android 7.x stability**: notification channels and foreground-service starts are guarded below API 26
- 🧪 **Testing mode** no longer copies OTPs to the clipboard
- 📊 **Lifetime rule activity** stored in `step_activity`, so retention cannot erase auto-pause evidence; reply and block steps are both credited
- 💾 **Durable pipeline export** through the system file picker; local snapshots are relabelled and their limits stated
- 🧾 **Strict YAML validation** rejects unknown keys and accepts document markers
- 🗓️ **Retention changes** run a cleanup immediately
- 🧹 **Removed** unused OpenRouter and Stop2EndPolicy code, the `INTERNET` permission, and the incorrect “encrypted storage” claim
- 🗃️ **Database v8** with `decisionReplyStep`, `step_activity`, and a migration that backfills activity

### **v1.4.0**
- 🧭 **Editable message pipeline**: ordered steps with checks and actions, `Block` as the only terminal action, end means allow
- 🧾 **YAML import/export** for pipelines and single step templates
- 🧪 **20-message sample lab** with a reference pipeline, your pipeline, and per-message breakdowns
- 🤖 **On-device model in the lab** to compare classifier verdicts with the rule tier
- 💡 **Suggested rules** as approvable templates with YAML and fixture impact
- 🎚️ **Enforcing and testing modes**: testing records decisions without performing them
- 🔍 **“Why was this filtered?”** per-message traces with every check and action
- 📋 **Test decisions queue** grouped by step, with filters and traces
- 📊 **Per-rule analytics** and optional 90-day stale-rule auto-pause
- 💾 **Daily local snapshots** of pipeline YAML and settings
- 🗓️ **Message retention**, default 2 months, for Junkboy's own database only

### **v1.3.0**
- 🎯 **Smart OTP Detection**: Complete rewrite of OTP extraction with pattern-based detection
  - 16+ high-confidence patterns for Turkish banks (İş Bankası, Vakıfbank, T.Finans, MARS, etc.)
  - Support for English OTPs (Apple, international services)
  - Delivery code detection (PTT, Yurtiçi, HepsiJET, DHL, KolayGelsin, TY Express)
  - Alphanumeric code support (NETGSM)
  - Smart exclusion of false positives (money amounts, dates, tracking numbers, phone numbers)
  - Accuracy improved from ~10% to 95%+
- 🔧 **Multipart SMS Fix**: Fixed duplicate notifications and split messages for long SMS
- 🔔 **Notification Fix**: Removed non-functional "Mark Read" button from notifications

### **v1.2.0**
- 📱 **Full SMS App**: Complete SMS client with conversation view, message composer, and send functionality
- ✅ **Allowed Senders Category**: Messages from whitelisted senders now show as "Allowed" category
- 🎛️ **SMS Display Categories**: Choose which message categories to display in the SMS screen
- ⌨️ **Keyboard Offset**: Accessibility setting to add bottom padding when keyboard is visible (for devices with physical keyboards)
- 🎨 **New App Icon**: Fresh AI-themed icon with chat bubbles and brain design
- 🧪 **Test Auto-Delete**: Button to test auto-delete junk functionality in settings
- 🔧 **Improved Test Filter**: Now respects allowed senders when testing messages
- 📊 **Clear All Data**: Now properly resets statistics along with message data

### **v1.1.0**
- 📥 **Hub Feature**: Optional unified inbox for SMS and chat notifications
- 💬 **Conversation View**: Messages grouped by sender in Hub and Filtered screens
- 🔇 **SMS App Control**: Buzzkill-like notification dismiss/mute for default SMS app
- 🎯 **Tap-to-Open**: Click messages to open conversations in default SMS app
- ⚙️ **Hub Settings**: Configurable display mode and default view
- 🔄 **Dynamic Navigation**: Navigation items adapt to enabled features

### **v1.0.0**
- ✨ **Initial Release**: Complete SMS filtering application
- 🤖 **AI Classification**: TensorFlow Lite integration
- 🎨 **Material Design 3**: Modern Jetpack Compose UI
- 🔔 **Smart Notifications**: Category-specific notification controls
- 📊 **Analytics Dashboard**: Comprehensive filtering statistics
- 🛠️ **Custom Filters**: Keywords, regex, and allowed senders
- 🗃️ **Data Management**: Export, import, and bulk processing
- 🚀 **Performance**: Optimized background processing

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

Special thanks to:
- **[Junkman: A.I. SMS Spam Blocker](https://apps.apple.com/tr/app/junkman-a-i-sms-spam-blocker/id1591815272)** by **Kerem Erkan** - This project was heavily inspired by Junkman's excellent approach to AI-powered SMS filtering on iOS. Kerem's pioneering work in on-device ML classification and privacy-first design served as the foundation for bringing similar functionality to Android.
- **[Buzzkill](https://play.google.com/store/apps/details?id=com.samruston.buzzkill)** - Inspiration for the notification control features
- **Cursor AI** - This project was built with the assistance of Cursor AI, which provided invaluable support in architecting the codebase, implementing complex features, and maintaining best practices throughout development.
- **TensorFlow Team** for the excellent Lite framework
- **Android Team** for Jetpack Compose and Material Design 3
- **Kotlin Team** for the amazing programming language
- **Open Source Community** for inspiration and best practices

---

## 📞 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/ovehbe/junkboy/issues)
- **Discussions**: [Community discussions and questions](https://github.com/ovehbe/junkboy/discussions)

---

**⭐ If you find Junkboy useful, please consider starring the repository to help others discover it!**
