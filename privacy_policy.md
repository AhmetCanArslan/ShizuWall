## ShizuWall Privacy Policy

**Last Updated:** November 11 2025

ShizuWall is a lightweight, privacy-focused firewall application for Android that blocks network connections for selected apps using Shizuku, without requiring root access or a VPN service. Your privacy is our utmost priority. This policy explains that the ShizuWall application does not collect, use, or share any personal data.

### 1. Data Collection and Use

**ShizuWall does not collect, use, process or share any personal data.**

* **No Personal Data:** The application does not collect any information that could identify you, such as your name, email address, device ID, or location.
* **Offline Operation:** ShizuWall has an "offline-first" design and does not require or use an internet connection to function.
* **No Analytics or Tracking:** There is no analytics, tracking mechanism, or telemetry within the application. The app does not transmit any data to external services.
* **No Advertising:** ShizuWall does not contain any advertisements.

### 2. Local Data Storage

ShizuWall stores only a minimal amount of data locally on your device to maintain its functionality:

* **Stored Data:**
    * **Selected Apps:** The package names of applications you have marked to have their network access blocked or allowed.
    * **Enabled Status:** A flag indicating whether the firewall framework is enabled or disabled.
    * **Boot Timestamp:** A small boot-relative timestamp stored in **device-protected storage** to safely detect device reboots and automatically clear stale saved state.
* **Nature of Data:** This data is entirely local, unencrypted, and only accessible by the application itself. This data is **never** sent off your device.

### 3. Permissions and Shizuku Usage

ShizuWall relies on the Shizuku service to perform its network control function.

* **Permissions Required:** The application uses the API provided by Shizuku to execute system commands. This grants access to Android's built-in connectivity service (`cmd connectivity`).
* **Mechanism:** ShizuWall does not intercept or inspect network traffic. Instead, it uses Shizuku to enable or disable the system-level network connection for your selected apps via Android’s native firewall framework (chain-3). This process does not involve creating a VPN tunnel or packet interception.
* **Disclaimer:** The developer of ShizuWall is not responsible for any issues arising from Shizuku usage, network blocking affecting system functionality, data loss, or service disruption. The user operates the application strictly at their own discretion and should ensure they understand which apps they are blocking.

### 4. License and Contributions

ShizuWall is licensed under the **GNU General Public License v3.0 (GPLv3)**. The open-source nature allows for transparency and independent verification of our privacy claims.

### 5. Changes to This Policy

This Privacy Policy may be updated from time to time. We will post any changes here. ShizuWall's core privacy principle—**the commitment not to collect personal data**—will remain unchanged.

### 6. Contact Us

If you have any questions about this Privacy Policy, please contact the developer via the communication channels specified on the application's repository.

---
