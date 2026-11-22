# Play Console Declarations & Rollout Checklist — Palometa

Use this guide when filling out **App content**, **Data safety**, and **Release** tracks in Google Play Console for `com.palometa.application`.

---

## Step 4 — Required Play Console Declarations

### 1. Privacy Policy URL
1. Push your repo to GitHub.
2. In GitHub repo **Settings → Pages → Build and deployment**:
   - Source: **Deploy from a branch**
   - Branch: `main` (or default branch), Folder: **`/docs`**
3. Wait ~1 minute for GitHub Pages to build, then copy your live URL:
   `https://<your-github-username>.github.io/<repo-name>/privacy.html`
4. Paste that URL into **Play Console → App content → Privacy policy**.

### 2. Data Safety Form
- **Does your app collect or share any of the required user data types?** → **No**
- **Is all user data collected by your app encrypted in transit?** → *(Skipped / N/A when No is selected)*
- **Do you provide a way for users to request that their data is deleted?** → *(N/A when No data is collected)*
- *Why:* Palometa runs 100% locally, uses no internet/network SDKs, and exports `.zip` bundles only when initiated by the user via Android SAF / Share Sheet.

### 3. App Access
- **Are all parts of your app available without restricted access?** → **All functionality in my app is available without any access restrictions** (no login or credentials needed).

### 4. Ads
- **Does your app contain ads?** → **No, my app does not contain ads.**

### 5. Content Rating (IARC)
- Category: **Utility, Productivity, Communication, or Other**
- Select **No** for violence, sexual content, profanity, controlled substances, gambling, user-to-user interaction, and sharing personal info/location.

### 6. Target Audience & Content
- Recommended Target Age Group: **18 and over** (avoids Google Play Families / Designed for Families policy restrictions since Palometa is a developer/security engineering tool).
- **Could your store listing appeal to children?** → **No**.

### 7. Financial Features & Government Apps
- **Financial features:** Select **My app doesn't provide any financial features**.
- **Government apps:** Select **No**.

### 8. Sensitive Permissions Status
- `AndroidManifest.xml` declares **zero** dangerous or restricted permissions (`QUERY_ALL_PACKAGES`, `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES` are all absent).
- Package visibility uses a targeted `<queries>` filter (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) as recommended by Play Policy.

---

## Step 5 — Testing & Production Rollout

1. **Generate Signed `.aab`:**
   - Via AI Studio **Settings → Generate AAB**, or locally with `:app:bundleRelease` using your `upload` keystore.
   - Verify **Play App Signing** is opted in when creating your first release.
2. **Internal Testing Track (First):**
   - Upload the `.aab` to **Testing → Internal testing**, add yourself as a tester, install from Play Store, and inspect the **Pre-launch report** for any automated warnings.
3. **Closed Testing (If Using a New Personal Developer Account):**
   - Personal Play Developer accounts created after November 2023 must run a **Closed test with at least 12 testers opted in continuously for 14 days** before Production access unlocks.
4. **Apply / Promote to Production:**
   - Once closed testing (if required) and all checks pass, promote the verified release to **Production** for Google Play review.
