# SKA Configurator

A Java Swing desktop application for creating and editing **Smart Key Attributes (SKA)** configuration files .

---

## Requirements

- **Java 21** or later

## Build

```bash
mvn clean package
```

Produces a runnable fat JAR at `target/ska-configurator-1.0-SNAPSHOT.jar` (~3.5 MB, all dependencies included).

## Run

```bash
java -jar target/ska-configurator-1.0-SNAPSHOT.jar
```

## Tests

```bash
mvn test
```

9 tests: unit tests for CSV import and XML read/write, plus an end-to-end integration test.

---

## Features

### File Operations

| Action | Shortcut | Description |
|---|---|---|
| **New Configuration** | Ctrl+N | Create a blank SKA configuration (prompts if unsaved changes exist) |
| **Open SKA XML** | Ctrl+O | Load an existing `.xml` configuration file |
| **Save** | Ctrl+S | Save to the current file (or Save As if no file yet) |
| **Save As** | Ctrl+Shift+S | Save to a new file, with overwrite confirmation |
| **Import Users from CSV** | Ctrl+I | Import users from a Jira CSV export |
| **Exit** | — | Close the application (prompts to save if unsaved changes) |

### Tabs

#### 1. Global Config

Top-level configuration metadata:

- **Module name** — free-text identifier (e.g. `EDOC-PP-CERT-01`)
- **Version** — integer version number
- **Per-section key settings** (Organization, SKA Plus, SKA Modify):
  - Key label (free text)
  - Start / End validity dates (YYYY-MM-DD format, validated on save)
  - Blocked on initialize (checkbox)

#### 2. Organization / 3. SKA Plus / 4. SKA Modify

Each section has the same layout:

- **EC Parameters**: predefined curve selector (brainpoolP256r1, brainpoolP384r1, brainpoolP512r1, prime256v1, secp384r1, secp521r1) or custom free-text entry, plus a PEM text area for raw EC parameters.
- **Operation sub-tabs** (Use / Modify / Block / Unblock) — each operation has:
  - Delay and time limit (milliseconds)
  - **Boundaries**: list of boundaries (add/remove), each containing:
    - **Groups**: list of groups (add/remove), each with:
      - Name, quorum (threshold)
      - Members mode (CNs) or Keys mode (key labels)
      - Manual entry or **Pick from Users** button to select from the imported user list
      - Apply button to commit group edits

#### 5. Keys (Proto)

Same layout as the section panels (EC parameters + 4 operation sub-tabs) but without key label/validity metadata.

#### 6. Users

User management table with columns: CN, Name, Email, Organisation, UserID, Certificate status (✓/—).

| Action | Description |
|---|---|
| **Add User** | Create a user manually (CN required, duplicate CN prevented) |
| **Edit User** | Modify an existing user's fields and certificate |
| **Remove User** | Delete a user (with confirmation; does not auto-remove from groups) |
| **View Certificate** | Display the user's PEM certificate in a read-only viewer |

### CSV Import

Imports users from Jira CSV asset exports (RFC 4180 format with multiline quoted fields):

- Automatically maps columns: `cn`, `Email Address`, `Organisation`, `User Id`, certificate fields
- Parses role columns using `||` delimiters for multi-value fields (Org Owner, Security Officer, Operator)
- Cleans `&nbsp;` artifacts from certificate data
- Handles certificates in unexpected columns (scans all fields)
- Deduplicates users by CN (merges data, keeps latest certificate, unions roles)
- **Certificate change detection**: when importing into an existing configuration, detects changed certificates and prompts to update
- **New user detection**: prompts to add users found in CSV but not in the current configuration

### Validation & Safety

- **Dirty flag**: title bar shows `*` when unsaved changes exist
- **Unsaved-changes guard**: New, Open, and Exit all prompt before discarding changes
- **Save validation warnings** (non-blocking — user can proceed):
  - Empty module name
  - Users without certificates
  - Groups with no members/keys
  - Date fields not in YYYY-MM-DD format
- **PEM format warning**: alerts if a certificate lacks `BEGIN/END CERTIFICATE` markers
- **Duplicate CN prevention**: cannot add two users with the same Common Name
- **Escape key**: closes dialog windows
- **Confirmation dialogs**: on boundary removal, group removal, user removal, file overwrite

### XML Output

- Follows the organization SKA configuration format (see `example/ska.xml`)
- Proper indentation for human readability
- CDATA sections for certificate content
- `curveName` attribute preserved on `ecParameters` elements
- XXE (XML External Entity) protection enabled on the reader

---

## Project Structure

```
src/main/java/com/pki/
├── App.java                    # Entry point (FlatLaf + MainFrame)
├── gui/
│   ├── MainFrame.java          # Main window, menus, file ops, dirty tracking
│   ├── GlobalConfigPanel.java  # Module name, version, per-section key metadata
│   ├── SectionPanel.java       # EC params + 4 operation sub-tabs
│   ├── KeysProtoPanel.java     # Same as SectionPanel for keys>proto
│   ├── EcParametersPanel.java  # Curve selector + PEM text area
│   ├── OperationPanel.java     # Boundaries, groups, members/keys editor
│   ├── UsersPanel.java         # User table with add/edit/remove
│   ├── UserEditDialog.java     # Modal form for user add/edit
│   └── UserPickerDialog.java   # Multi-select dialog for group membership
├── io/
│   ├── CsvImporter.java        # Jira CSV parser (OpenCSV, RFC 4180)
│   ├── SkaXmlReader.java       # DOM XML reader (XXE disabled)
│   └── SkaXmlWriter.java       # DOM XML writer (indented, CDATA certs)
└── model/
    ├── SkaConfig.java           # Root: moduleName, version, sections, users
    ├── SkaSection.java          # keyLabel, validity, blockedOnInit, ecParams, ops
    ├── KeysProto.java           # Keys>proto: ecParams + operations
    ├── Operations.java          # Container: use, modify, block, unblock
    ├── Operation.java           # delay, timeLimit, list of boundaries
    ├── Boundary.java            # List of groups
    ├── Group.java               # name, quorum, memberCns, keyLabels
    ├── User.java                # cn, name, email, org, userId, certificate, roles
    └── EcParameters.java        # curveName, pemText
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| OpenCSV | 5.9 | CSV parsing (RFC 4180, multiline fields) |
| FlatLaf | 3.4 | Modern Swing look-and-feel |
| JUnit 4 | 4.13.2 | Unit testing |
